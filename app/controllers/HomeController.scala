/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import com.ideal.linked.toposoid.common.{SentenceType, ScopeType,  FeatureType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState, RelationMatchState}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseEdge, KnowledgeBaseNode}
import com.ideal.linked.toposoid.protocol.model.base.{KnowledgeBaseSideInfo, _}
import com.ideal.linked.toposoid.protocol.model.neo4j.{Neo4jRecordMap, Neo4jRecords}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import play.api.mvc._
import play.api.libs.json.JsValue

import javax.inject._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.AuthenticityType
import com.ideal.linked.toposoid.common.InMemoryDbUtils
import com.ideal.linked.toposoid.protocol.model.frontend.Endpoint
import com.ideal.linked.toposoid.common.ActionModeType
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.SingleFeatureVectorForSearch
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.FeatureVectorSearchResult
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.FeatureVectorIdentifier
import com.ideal.linked.toposoid.common.Neo4JUtilsImpl

case class FeatureVectorSearchInfo(propositionId:String, sentenceId:String, sentenceType:Int, lang:String, featureId:String, similarity:Float)
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with LazyLogging {

  final val NO_HOST = "-"
  final val NO_PORT = "-"
  final val NO_NAME = "-"

  /**
   * This function receives a parser's result as JSON,
   * checks whether it matches logically strictly with the knowledge database, and returns the result in JSON.
   */
  def execute():Action[JsValue] = Action(parse.json[JsValue]) { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos:List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects
      val currentEndPoints = InMemoryDbUtils.getEmbedingDeducitonUnitEndPoints(transversalState)
      val result = deduce(0, json.toString(), json.toString(), currentEndPoints, transversalState)
      logger.info(ToposoidUtils.formatMessageForLogger("embedding deduction completed.", transversalState.userId))      
      Ok(result._3).as(JSON)
    }catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }
  private def extractKnowledgeBaseSideInfo(coveredPropositionEdges: List[CoveredPropositionEdge]):List[KnowledgeBaseSideInfo] = {
    
    coveredPropositionEdges.foldLeft(List.empty[KnowledgeBaseSideInfo]){
      (acc, x) => {
        
        //同一のsentenceIdを持っているものが対象なのでフィルターする。
        val sourceSentenceIds = x.sourceNode.isConfirmed match  {
          case true => x.sourceNode.matchedKnowledgeNodes.map(y => y.sentenceId).toSet
          case _ => Set()
        }        
        val destinationSentenceIds = x.destinationNode.isConfirmed match  {
          case true => x.destinationNode.matchedKnowledgeNodes.map(y => y.sentenceId).toSet
          case _ => Set()
        }        
        val confirmedSentenceIds  = sourceSentenceIds & destinationSentenceIds
        val distinctMatchedKnowledgeNodes = (x.sourceNode.matchedKnowledgeNodes:::x.destinationNode.matchedKnowledgeNodes).filter(y =>{
          confirmedSentenceIds.contains(y.sentenceId)
        }).distinct

        //val deductionUnitsByNodeId = x.sourceNode.matchedKnowledgeNodes.map(y => (y.nodeId -> x.sourceNode.deductionUnit)).toMap ++ x.destinationNode.matchedKnowledgeNodes.map(y => (y.nodeId -> x.destinationNode.deductionUnit)).toMap

        val deductionUnits = x.sourceNode.matchedKnowledgeNodes.map(y => y.deductionUnit) ::: x.destinationNode.matchedKnowledgeNodes.map(y => (y.deductionUnit))

        if(confirmedSentenceIds.size > 0){
          val confirmedKnowledgeBaseSideInfoList:List[KnowledgeBaseSideInfo] = distinctMatchedKnowledgeNodes.map(y => {
            KnowledgeBaseSideInfo(
              propositionId = y.propositionId,
              sentenceId = y.sentenceId,
              featureInfoList =List(y.featureInfo),
              deductionUnits = deductionUnits
            )
          })
          acc ::: confirmedKnowledgeBaseSideInfoList
        }else{
          acc
        }
      }
    }
  }  
  /**
   * final check
   *
   * @param targetMatchedPropositionInfoList
   * @param aso
   * @param searchResults
   * @return
   */
  
  private def checkFinal(aso: AnalyzedSentenceObject, deductionUnitName:String, unsettledCoveredPropositionEdges:List[CoveredPropositionEdge], transversalState:TransversalState ): AnalyzedSentenceObject = {

    val extractedKnowledgeBaseSideInfo =  extractKnowledgeBaseSideInfo(unsettledCoveredPropositionEdges)
    //このdeductionUnitは、全てカバーかそうでないかのどちらかの結果になるので、extractedKnowledgeBaseSideInfoのサイズが0より大きければ全てカバーとなる。

    //AnalysisSentenceObjectは必ず文章は一つ
    //TODO:knowledgeBaseSideInfoListは、ある閾値を超えてるのであればいくつか候補が欲しい。ただし類似度が高い順に返して欲しい。
    if(extractedKnowledgeBaseSideInfo.size == 0) return aso
    val updatedKnowledgeBaseSideInfo = getCoveredKnowledgeBaseSideInfo(extractedKnowledgeBaseSideInfo, aso, transversalState)
    if(updatedKnowledgeBaseSideInfo.size == 0) return aso
    val status = true
    val deductionResult: DeductionResult = new DeductionResult(status,AuthenticityType.TRUE.index, unsettledCoveredPropositionEdges, updatedKnowledgeBaseSideInfo)
    AnalyzedSentenceObject(aso.nodeMap, aso.edgeList, aso.knowledgeBaseSemiGlobalNode, deductionResult)

  }


  private def getCoveredKnowledgeBaseSideInfo(extractedKnowledgeBaseSideInfo:List[KnowledgeBaseSideInfo], aso: AnalyzedSentenceObject,  transversalState:TransversalState) :List[KnowledgeBaseSideInfo]  ={
    //TODO:ここはFilterではうまく行かない。Premiseを立証するClaimの情報が追加されないといけない！！！
    extractedKnowledgeBaseSideInfo.foldLeft(List.empty[KnowledgeBaseSideInfo]){
      (acc, x) => {
        if (havePremise(x, transversalState)) {
          acc ::: checkClaimHavingPremise(x, transversalState)
        } else {
          acc :+ x
        }
      }
    }.distinct 
  }


  private def havePremise(knowledgeBaseSideInfo: KnowledgeBaseSideInfo, transversalState:TransversalState): Boolean = {
    val neo4jUtils = Neo4JUtilsImpl()
    val query = "MATCH (n:SemiGlobalPremiseNode)-[*]-(m:SemiGlobalClaimNode) WHERE m.propositionId ='%s' AND m.sentenceId ='%s'  RETURN (n)".format(knowledgeBaseSideInfo.propositionId, knowledgeBaseSideInfo.sentenceId)
    val jsonStr: String = neo4jUtils.getCypherQueryResult(query, "n", transversalState)
    val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
    neo4jRecords.records.size match {
      case 0 => false
      case _ => true
    }
  }
  
      /**
   *
   * @param targetMatchedPropositionInfoList
   * @return
   */
  private def checkClaimHavingPremise(knowledgeBaseSideInfo: KnowledgeBaseSideInfo, transversalState:TransversalState): List[KnowledgeBaseSideInfo] = {
    //Pick up a node with the same surface layer as the Premise connected from Claim as x
    //Search for the one that has the corresponding ClaimId and has a premise
    val neo4jUtils = Neo4JUtilsImpl()
    val query = "MATCH (n:SemiGlobalPremiseNode) WHERE n.propositionId='%s' RETURN n".format(knowledgeBaseSideInfo.propositionId, knowledgeBaseSideInfo.propositionId)
    val jsonStr = neo4jUtils.getCypherQueryResult(query, "x", transversalState)
    val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
    neo4jRecords.records.size match {
      case 0 => List.empty[KnowledgeBaseSideInfo]
      case _ => checkOnlyClaimNodes(neo4jRecords, knowledgeBaseSideInfo, transversalState)
    }
  }

  def getMatchedSentenceFeature(originalSentenceId: String, originalSentenceType: Int, sentence: String, lang: String, transversalState:TransversalState): List[KnowledgeBaseSideInfo] = {

    val vector = FeatureVectorizer.getSentenceVector(Knowledge(sentence, lang, "{}"), transversalState)
    val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_SENTENCE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
    val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
    val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]

    //VecotrDBにClaimとして存在している場合かつ精度よく一致しているものが存在している場合にのみ推論が可能になる。前提が間違っていると推論は破綻する立場をとる。
    val (ids, similarities) = (result.ids zip result.similarities).foldLeft((List.empty[FeatureVectorIdentifier], List.empty[Float])) {
      (acc, x) => {
        x._1.sentenceType match {
          case SentenceType.CLAIM.index => x._2 > conf.getDouble("TOPOSOID_EMBEDDING_DEDUCTION_PREMISE_MATCH_THRESHOLD") match {
            case true => (acc._1 :+ x._1, acc._2 :+ x._2)
            case _ => acc
          } 
          case _ => acc
        }
      }
    }

    val filteredResult = FeatureVectorSearchResult(ids, similarities, result.statusInfo)
    filteredResult.ids.size match {
      case 0 => List.empty[KnowledgeBaseSideInfo]
      case _ => {
        //sentenceごとに最も類似度が高いものを抽出する
        val featureVectorSearchInfoList = extractExistInNeo4JResultForSentence(filteredResult, originalSentenceType, transversalState)
        featureVectorSearchInfoList.map(x => {          
          //TODO:deductionUnitsを適切にコンストラクタに渡す
          KnowledgeBaseSideInfo(x.propositionId, x.sentenceId, List(MatchedFeatureInfo(featureId = x.sentenceId, FeatureType.SENTENCE.index, similarity = x.similarity)), List.empty[String])
        })
      }
    }
  }
  
  private def extractExistInNeo4JResultForSentence(featureVectorSearchResult: FeatureVectorSearchResult, originalSentenceType: Int, transversalState:TransversalState): List[FeatureVectorSearchInfo] = {

    val neo4jUtils = Neo4JUtilsImpl()
    (featureVectorSearchResult.ids zip featureVectorSearchResult.similarities).foldLeft(List.empty[FeatureVectorSearchInfo]) {
      (acc, x) => {
        val idInfo = x._1
        val propositionId = idInfo.superiorId
        val lang = idInfo.lang
        val featureId = idInfo.featureId
        val similarity = x._2
        val nodeType: String = ToposoidUtils.getNodeType(idInfo.sentenceType, ScopeType.SEMIGLOBAL.index, FeatureType.SENTENCE.index)
        //Check whether featureVectorSearchResult information exists in Neo4J
        val query = "MATCH (n:%s) WHERE n.propositionId='%s' AND n.sentenceId='%s' RETURN n".format(nodeType, propositionId, featureId)
        val jsonStr: String = neo4jUtils.getCypherQueryResult(query, "", transversalState)
        val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
        neo4jRecords.records.size match {
          case 0 => acc
          case _ => {
            val idInfoOnNeo4jSide = neo4jRecords.records.head.head.value.semiGlobalNode.get
            //sentenceType returns the originalSentenceType of the argument
            acc :+ FeatureVectorSearchInfo(idInfoOnNeo4jSide.propositionId, idInfoOnNeo4jSide.sentenceId, originalSentenceType, lang, featureId, similarity)
          }
        }
      }
    }
  }
  /**
   *
   * @param neo4jRecords
   * @param targetMatchedPropositionInfoList
   * @return
   */
  private def checkOnlyClaimNodes(neo4jRecords: Neo4jRecords, knowledgeBaseSideInfo: KnowledgeBaseSideInfo, transversalState:TransversalState): List[KnowledgeBaseSideInfo] = {

    val claimMatchedPropositionInfo = neo4jRecords.records.foldLeft(List.empty[KnowledgeBaseSideInfo]){
      (acc, x) => {
        //得られたすべてのPremiseについてClaimが存在するかをチェック　
        val originalSentenceId = x.head.value.semiGlobalNode.get.sentenceId
        val originalSentenceType = x.head.value.semiGlobalNode.get.sentenceType
        val sentence = x.head.value.semiGlobalNode.get.sentence
        val lang = x.head.value.semiGlobalNode.get.localContextForFeature.lang

        acc ::: getMatchedSentenceFeature(originalSentenceId, originalSentenceType, sentence, lang, transversalState)
      }
    }

    //Checkpoint
    //・Are there all claims corresponding to premise?
    //・Does the obtained result have more propositionIds than the number of neo4jRecords records?得られてた結果でneo4jRecordsのレコード数と同数以上のpropositionIdを持つものが存在するかどうか？
    //・Multiple claims can guarantee one Premise, so it is not necessarily =, but there must be more Claims than the number of Premises.
    if (claimMatchedPropositionInfo.size < neo4jRecords.records.size) return List.empty[KnowledgeBaseSideInfo]

    //val candidates: List[MatchedPropositionInfo] = claimMatchedPropositionInfo.groupBy(identity).mapValues(_.size).map(_._1).toList
    val candidates: List[KnowledgeBaseSideInfo] = claimMatchedPropositionInfo.distinct
    //candidatesは、propositionId上の重複はない。
    if (candidates.size == 0) return List.empty[KnowledgeBaseSideInfo]
    //ensure there are no Premise. only claim!
    val finalChoice: List[KnowledgeBaseSideInfo] = candidates.filterNot(x => this.havePremise(x, transversalState))
    finalChoice.size match {
      case 0 => List.empty[KnowledgeBaseSideInfo]
      case _ => finalChoice :+ knowledgeBaseSideInfo //finalChoice:premiseを説明するClaim knowledgeBaseSideInfo: premiseに接続しているClaim
    }

  }

  /**
   * This function analyzes whether the entered text exactly matches.
   *
   * @param aso
   * @param asos
   * @return
   */
  
  def analyze(aso: AnalyzedSentenceObject, asos: List[AnalyzedSentenceObject], deductionUnitName:String, /*deductionUnitFeatureTypes:List[Int],*/ transversalState:TransversalState): AnalyzedSentenceObject = {
    //Excluding those for which the existence of links has already been confirmed in edgeList
    //val coveredPropositionEdges:List[CoveredPropositionEdge] = analyzeGraphKnowledge(getUnsettledEdges(aso), aso, transversalState)
    
    //TODO:要修正　そもそもそのDeductionUnitで推論する意味があるのかをハードコーディングするのはちょと、、、
    //一旦、暫定処置
    val deductionUnitFeatureTypes = List.empty[Int]

    //if(!haveFeatureTypeToProcess(aso, deductionUnitFeatureTypes)) return aso
    val coveredPropositionEdges = aso.deductionResult.coveredPropositionEdges
    if (coveredPropositionEdges.size == 0) return aso
    val result = checkFinal(aso, deductionUnitName, coveredPropositionEdges, transversalState)
    if(!result.deductionResult.status) return result
    //This process requires that the Premise has already finished in calculating the DeductionResult
    if (aso.knowledgeBaseSemiGlobalNode.sentenceType == SentenceType.CLAIM.index) {

      //val premiseDeductionResults: List[DeductionResult] = asos.map(x => x.deductionResultMap.get(PREMISE.index.toString).get)
      val premiseDeductionResults: List[DeductionResult] = asos.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType == SentenceType.PREMISE.index).map(y => y.deductionResult)
      //If there is no deduction result that makes premise true, return the process.
      if (premiseDeductionResults.filter(_.status).size == 0) return result
      asos.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType == SentenceType.PREMISE.index).size match {
        case 0 => result
        case _ => {
          val knowledgeBaseSideInfoList = premiseDeductionResults.map(y => y.evidenceKnowledgeList).flatten
          val premisePropositionIds: Set[String] = knowledgeBaseSideInfoList.map(_.propositionId).toSet
          //Depending on the conditions, the result is claim information.
          val claimPropositionIds:Set[String] = result.deductionResult.evidenceKnowledgeList.map(_.propositionId).toSet
          //There must be at least one Claim that corresponds to at least one Premise proposition.
          (premisePropositionIds & claimPropositionIds).size - premisePropositionIds.size match {
            case 0 => {
              //val originalDeductionResult: DeductionResult = result.deductionResultMap.get(CLAIM.index.toString).get
              val originalDeductionResult: DeductionResult = result.deductionResult
              val updateDeductionResult: DeductionResult = DeductionResult(
                status = originalDeductionResult.status,
                authenticityType = AuthenticityType.TRUE.index,
                coveredPropositionEdges = originalDeductionResult.coveredPropositionEdges,
                //coveredPropositionResults = originalDeductionResult.coveredPropositionResults,
                evidenceKnowledgeList = knowledgeBaseSideInfoList,
                havePremiseInGivenProposition = true
              )
              AnalyzedSentenceObject(
                nodeMap = result.nodeMap,
                edgeList = result.edgeList,
                knowledgeBaseSemiGlobalNode = result.knowledgeBaseSemiGlobalNode,
                deductionResult = updateDeductionResult
              )
            }
            case _ => result
          }
        }
      }
    } else {
      result
    }
  }
  

  private def deduce(index:Int, targetJson:String, resultJson:String, endPoints:Seq[Endpoint], transversalState:TransversalState): (Int, String, String) ={
    val asosJson = execute(endPoints(index), targetJson, resultJson, transversalState)
    if(index == endPoints.size -1){
      (index, asosJson._1, asosJson._2)
    }else{
      deduce(index + 1, asosJson._1, asosJson._2, endPoints, transversalState)
    }
  }

  private def execute(endpoint:Endpoint, targetJson:String, resultJson:String, transversalState:TransversalState): (String, String) ={

    if(endpoint.host.equals(NO_HOST) || endpoint.port.equals(NO_PORT) || endpoint.name.equals(NO_NAME)) return (targetJson, resultJson)
    val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(resultJson).as[AnalyzedSentenceObjects]
    val notFinished = analyzedSentenceObjects.analyzedSentenceObjects.filterNot(x => x.deductionResult.status) 
    if(notFinished.size > 0) {
      val targets:List[AnalyzedSentenceObject] = notFinished
      //TODO:元々設定されていたDeductionConfigurationにする
      //val deductionCofiguration = DeductionConfiguration(ActionModeType.DEDUCTION_MODE.index, "", Map.empty[String,String])      
      val result = ToposoidUtils.callComponent(            
            //Json.toJson(AnalyzedSentenceObjects(analyzedSentenceObjects = targets, deductionConfiguration = deductionCofiguration)).toString(),
            resultJson,
            endpoint.host,
            endpoint.port,
            "execute",
            transversalState)
      logger.info(ToposoidUtils.formatMessageForLogger(endpoint.name + " finished.", transversalState.userId))
      getResultJson(result, resultJson, endpoint, transversalState)
    }else{
      (targetJson, resultJson)
      //getResultJson(Json.toJson(List.empty[VerifyingEdges]).toString, resultJson, endpoint, transversalState)
    }
  }

  private def getResultJson(targetEdgesJson:String, resultJson:String, endpoint:Endpoint, transversalState:TransversalState):(String,String) ={
    
    val targetEdges = Json.parse(targetEdgesJson).as[List[VerifyingEdges]]
    val resultAsos = Json.parse(resultJson).as[AnalyzedSentenceObjects]
    //Premiseを先に処理する。
    val asos = resultAsos.analyzedSentenceObjects.sortBy(z => z.knowledgeBaseSemiGlobalNode.sentenceType).foldLeft(List.empty[AnalyzedSentenceObject]){
      (acc, x) => {
        val coveredPropositionEdges:List[VerifyingEdges] = targetEdges.filter(_.sentenceId.equals(x.knowledgeBaseSemiGlobalNode.sentenceId))
        val analyzedAso:AnalyzedSentenceObject = coveredPropositionEdges.size match {
          case 0 => x
          case _ => {
            val confirmedCoveredPropositionEdges = x.deductionResult.coveredPropositionEdges.filter(y => y.sourceNode.isConfirmed && y.destinationNode.isConfirmed)
            val deductionReulst = DeductionResult(
              status = x.deductionResult.status, 
              authenticityType = x.deductionResult.authenticityType, 
              coveredPropositionEdges = confirmedCoveredPropositionEdges ++ coveredPropositionEdges.head.coveredPropositionEdges, 
              evidenceKnowledgeList = x.deductionResult.evidenceKnowledgeList, 
              havePremiseInGivenProposition = x.deductionResult.havePremiseInGivenProposition,
              deductionPhaseType = x.deductionResult.deductionPhaseType)
            val aso = AnalyzedSentenceObject(x.nodeMap, x.edgeList, x.knowledgeBaseSemiGlobalNode, deductionReulst)
            analyze(aso, acc, endpoint.name, transversalState)
          }
        }
        acc :+ analyzedAso
      }
    }
    val updateResultJson = Json.toJson(AnalyzedSentenceObjects(asos, resultAsos.deductionConfiguration)).toString()
    (resultJson, updateResultJson)
  }

}
