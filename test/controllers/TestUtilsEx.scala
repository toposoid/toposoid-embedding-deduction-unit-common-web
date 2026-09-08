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

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{Neo4JUtilsImpl, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.FeatureVectorIdentifier
import com.ideal.linked.toposoid.knowledgebase.regist.model.PropositionRelation
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils
import play.api.libs.json.Json
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObject
import com.ideal.linked.toposoid.protocol.model.base.VerifyingEdges
import com.ideal.linked.toposoid.protocol.model.base.DeductionResult
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseSemiGlobalNode
import com.ideal.linked.toposoid.knowledgebase.model.LocalContextForFeature
import com.ideal.linked.toposoid.common.DataEntryType
import com.ideal.linked.toposoid.common.FeatureType
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeFeatureReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import com.ideal.linked.toposoid.knowledgebase.regist.model.Reference
import com.ideal.linked.toposoid.knowledgebase.regist.model.ImageReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge
import com.ideal.linked.toposoid.protocol.model.frontend.Endpoint

//case class ImageBoxInfo(x:Int, y:Int, weight:Int, height:Int)

object TestUtilsEx {
  val neo4JUtils = new Neo4JUtilsImpl()
  def deleteNeo4JAllData(transversalState:TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    neo4JUtils.executeQuery(query, transversalState)
  }

  def executeQueryAndReturn(query:String, transversalState:TransversalState): Neo4jRecords = {
    neo4JUtils.executeQueryAndReturn(query, transversalState)
  }

  def registerSingleClaim(knowledgeForParser: KnowledgeForParser, transversalState: TransversalState): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    TestUtils.registerData(knowledgeSentenceSetForParser, transversalState, addVectorFlag = true)
    Thread.sleep(5000)
  }

  def deleteFeatureVector(featureVectorIdentifier: FeatureVectorIdentifier, transversalState:TransversalState): Unit = {
    val json: String = Json.toJson(featureVectorIdentifier).toString()
    ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "delete", transversalState)
  }
  var usedUuidList = List.empty[String]

  def getUUID(): String = {
    var uuid: String = java.util.UUID.randomUUID().toString
    while (usedUuidList.filter(_.equals(uuid)).size > 0) {
      uuid = java.util.UUID.randomUUID().toString
    }
    usedUuidList = usedUuidList :+ uuid
    uuid
  }
  /*
  def getKnowledge(lang:String, sentence: String, reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState:TransversalState): Knowledge = {
    Knowledge(sentence, lang, "{}", false, List(getImageInfo(reference, imageBoxInfo, transversalState)))
  }
  
  def getImageInfo(reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState:TransversalState): KnowledgeForImage = {
    val imageReference = ImageReference(reference: Reference, imageBoxInfo.x, imageBoxInfo.y, imageBoxInfo.weight, imageBoxInfo.height)
    val knowledgeForImage = KnowledgeForImage(id = getUUID(), imageReference = imageReference)
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "registImage",
      transversalState)
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }
  
  def addImageInfoToAnalyzedSentenceObjects(lang:String,inputSentence: String, knowledgeForImages: List[KnowledgeForImage], transversalState:TransversalState): String = {
    /**
     * CAUTION This function does not support cases where one node has multiple images!!!
     */
    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
      case "en_US" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
    }

    val asos: AnalyzedSentenceObjects = Json.parse(json).as[AnalyzedSentenceObjects]
    val updatedAsos = asos.analyzedSentenceObjects.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {

        val knowledgeForImage = knowledgeForImages(acc.size)

        val knowledgeFeatureReference = KnowledgeFeatureReference(
          propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
          sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
          featureId = getUUID(),
          featureType = FeatureType.IMAGE.index,
          url = knowledgeForImage.imageReference.reference.url,
          source = knowledgeForImage.imageReference.reference.originalUrlOrReference,
          featureInputType = DataEntryType.MANUAL.index,
          extentText = "{}")

        val localContextForFeature = LocalContextForFeature(
          x.knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
          List(knowledgeFeatureReference))

        val knowledgeBaseSemiGlobalNode = KnowledgeBaseSemiGlobalNode(
          sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
          propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
          documentId = x.knowledgeBaseSemiGlobalNode.documentId,
          sentence = x.knowledgeBaseSemiGlobalNode.sentence,
          sentenceType = x.knowledgeBaseSemiGlobalNode.sentenceType,
          localContextForFeature = localContextForFeature)


        acc :+ AnalyzedSentenceObject(
          nodeMap = x.nodeMap,
          edgeList = x.edgeList,
          knowledgeBaseSemiGlobalNode = knowledgeBaseSemiGlobalNode,
          deductionResult = x.deductionResult)
      }
    }
    Json.toJson(AnalyzedSentenceObjects(updatedAsos, asos.deductionConfiguration)).toString()
  }


  def analyzeByBaseDeductionUnitForSemiGlobal(asosJson:String, transversalState: TransversalState):String = {
  
    val deductionUnitHosts = Json.parse(conf.getString("TOPODOID_EMBEDDING_DEDUCTION_UNITS")).as[List[String]]
    val deductionUnitPorts = Json.parse(conf.getString("TOPODOID_EMBEDDING_DEDUCTION_PORTS")).as[List[String]]
    val deductionUnitNames = Json.parse(conf.getString("TOPODOID_EMBEDDING_DEDUCTION_NAMES")).as[List[String]]
    val endPoint: Endpoint = deductionUnitHosts.lazyZip(deductionUnitPorts).lazyZip(deductionUnitNames).map { (x, y, z) =>
      Endpoint(x,y,z)
    }.filter(x => x.name.equals("EmbeddingSentenceMatch")).head

    val json = ToposoidUtils.callComponent(asosJson, endPoint.host, endPoint.port, "execute", transversalState)
    val verifyingEdges = Json.parse(json).as[List[VerifyingEdges]]
    val analyzedSentenceObjects = Json.parse(asosJson).as[AnalyzedSentenceObjects]
    val asos = analyzedSentenceObjects.analyzedSentenceObjects
    
    val updatedAsos = asos.foldLeft(List.empty[AnalyzedSentenceObject]){
      (acc, x) => {
        val coveredPropositionEdges = verifyingEdges.filter(y => y.sentenceId.equals(x.knowledgeBaseSemiGlobalNode.sentenceId)).head.coveredPropositionEdges
        val updatedDeductionReult = DeductionResult(
          status = x.deductionResult.status, 
          authenticityType = x.deductionResult.authenticityType, 
          coveredPropositionEdges = coveredPropositionEdges, 
          evidenceKnowledgeList = x.deductionResult.evidenceKnowledgeList, 
          havePremiseInGivenProposition = x.deductionResult.havePremiseInGivenProposition, 
          deductionPhaseType = x.deductionResult.deductionPhaseType
        )        
        acc :+ AnalyzedSentenceObject(x.nodeMap, x.edgeList, x.knowledgeBaseSemiGlobalNode, updatedDeductionReult)
      }
    }
    Json.toJson(AnalyzedSentenceObjects(updatedAsos, analyzedSentenceObjects.deductionConfiguration)).toString
    
  }
  */
}
