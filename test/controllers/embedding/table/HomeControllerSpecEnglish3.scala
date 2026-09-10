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

package controllers.embedding.table

import org.apache.pekko.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{SentenceType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{PropositionRelation, Reference}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils.{uploadTable, getAnalyzedSentenceObjectsJsonForSemiGlobal, registerData, setDeductionUnitEndPoints}
import controllers.TestUtilsEx.{getUUID, registerSingleClaim, deleteNeo4JAllData}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test._

import scala.concurrent.duration.DurationInt
import com.ideal.linked.toposoid.common.ActionModeType
import com.ideal.linked.toposoid.protocol.model.frontend.Endpoint
import com.ideal.linked.toposoid.common.InMemoryDbUtils
import controllers.TestUtilsEx
//import controllers.ImageBoxInfo
import controllers.HomeController
import com.ideal.linked.toposoid.common.DeductionPhaseType
import com.ideal.linked.toposoid.knowledgebase.regist.model.ImageReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge
import com.ideal.linked.toposoid.knowledgebase.regist.model.TableReference
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable


class HomeControllerSpecEnglish3 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    deleteNeo4JAllData(transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    /*
    val deductionUnitHosts = Json.parse(conf.getString("TOPODOID_EMBEDDING_DEDUCTION_UNITS")).as[List[String]]
    val deductionUnitPorts = Json.parse(conf.getString("TOPODOID_EMBEDDING_DEDUCTION_PORTS")).as[List[String]]
    val deductionUnitNames = Json.parse(conf.getString("TOPODOID_EMBEDDING_DEDUCTION_NAMES")).as[List[String]]
    val endPoints: Seq[Endpoint] = deductionUnitHosts.lazyZip(deductionUnitPorts).lazyZip(deductionUnitNames).map { (x, y, z) =>
      Endpoint(x,y,z)
    }.toSeq
    InMemoryDbUtils.setEmbedingDeducitonUnitEndPoints(endPoints, transversalState)    
    */
    setDeductionUnitEndPoints(DeductionPhaseType.DEDUCTION_SENTENCE_BASE, transversalState, selectIndice = List(0,2))
    deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    deleteNeo4JAllData(transversalState)
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]

  val sentenceA = "There is evidentiary data that decisively determines the outcome."
  val referenceA = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table1.xlsx")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000001086170&fileKind=0")
  val tableReferenceA = TableReference(referenceA, skipHeaderRows=5, skipRowList=List(),multiHeaderRows=4, sheetNameForExcel= "se0101")
  val knowledgeForTableA = KnowledgeForTable(getUUID(), tableReferenceA)  

  val sentenceB = "I will submit the evidence data."
  val referenceB = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table2.csv")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000040292480&fileKind=1")
  val tableReferenceB = TableReference(referenceB, skipHeaderRows=8, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")  
  val knowledgeForTableB = KnowledgeForTable(getUUID(), tableReferenceB)    
  
  val sentenceC = "Evidence data1 is inevitably required."
  val referenceC = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table3.xls")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000040410921&fileKind=4")
  val tableReferenceC = TableReference(referenceC, skipHeaderRows=3, skipRowList=List(),multiHeaderRows=3, sheetNameForExcel= "")  
  val knowledgeForTableC = KnowledgeForTable(getUUID(), tableReferenceC)  

  val sentenceD = "It depends on the evidence data."  
  val referenceD = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table4.xlsx")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000032117292&fileKind=0")
  val tableReferenceD = TableReference(referenceD, skipHeaderRows=2, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")  
  val knowledgeForTableD = KnowledgeForTable(getUUID(), tableReferenceD)  

  val paraphraseA = "There is evidentiary sample that decisively determines the outcome."
  val referenceParaA = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table1.xlsx")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000001086170&fileKind=0")  
  val tableReferenceParaA = TableReference(referenceParaA, skipHeaderRows=5, skipRowList=List(),multiHeaderRows=4, sheetNameForExcel= "se0101")
  val knowledgeForTableParaA = KnowledgeForTable(getUUID(), tableReferenceParaA)

  val paraphraseB = "I will submit the evidence sample."
  val referenceParaB = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table2.csv")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000040292480&fileKind=1")
  val tableReferenceParaB = TableReference(referenceParaB, skipHeaderRows=8, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")
  val knowledgeForTableParaB = KnowledgeForTable(getUUID(), tableReferenceParaB)  

  val paraphraseC = "Evidence sample1 is inevitably required."
  val referenceParaC = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table3.xls")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000040410921&fileKind=4")
  val tableReferenceParaC = TableReference(referenceParaC, skipHeaderRows=3, skipRowList=List(),multiHeaderRows=3, sheetNameForExcel= "")
  val knowledgeForTableParaC = KnowledgeForTable(getUUID(), tableReferenceParaC)  

  val paraphraseD = "It depends on the evidence sample."
  val referenceParaD = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table4.xlsx")
  //  originalUrlOrReference = "https://www.e-stat.go.jp/stat-search/file-download?statInfId=000032117292&fileKind=0")
  val tableReferenceParaD = TableReference(referenceParaD, skipHeaderRows=2, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")
  val knowledgeForTableParaD = KnowledgeForTable(getUUID(), tableReferenceParaD)  
 

  val lang = "en_US"

  "The specification21" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      //val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge2), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge3), transversalState)
      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification22" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2), KnowledgeForParser(propositionId1, sentenceId3, knowledge3)),
        List(PropositionRelation("AND", 0,1)))
      registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification23" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val knowledge1a = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge2), KnowledgeForParser(propositionId2, sentenceId4, knowledge3)),
        List(PropositionRelation("AND", 0,1)))
      registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 2)
    }
  }

  "The specification23A" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val propositionId3 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val sentenceId4 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2), transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId3, sentenceId3, knowledge3), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 2)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification24" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      //val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledge1)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledge2)),
        List.empty[PropositionRelation])
      registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification25" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      //val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val knowledge1a = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge2)),
        List.empty[PropositionRelation])
      registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      //assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 1)
    }
  }

  "The specification26" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      //val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)
      val knowledge1a = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId2, sentenceId2, knowledge1a)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId2, sentenceId3, knowledge3)),
        List.empty[PropositionRelation])
      registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      //assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 1)
    }
  }

  "The specification27" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      //val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      //val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))
      

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))
      val paraphrase4 = Knowledge(lang=lang, sentence=paraphraseD, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaD, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification28" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      //val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))
      

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))
      val paraphrase4 = Knowledge(lang=lang, sentence=paraphraseD, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaD, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge2), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification29" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      //val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))
      

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))
      val paraphrase4 = Knowledge(lang=lang, sentence=paraphraseD, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaD, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge3), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)
      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

  "The specification30" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(lang=lang, sentence=sentenceA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableA, transversalState)))
      //val knowledge2 = Knowledge(lang=lang, sentence=sentenceB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableB, transversalState)))
      //val knowledge3 = Knowledge(lang=lang, sentence=sentenceC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableC, transversalState)))
      val knowledge4 = Knowledge(lang=lang, sentence=sentenceD, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableD, transversalState)))

      val paraphrase1 = Knowledge(lang=lang, sentence=paraphraseA, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaA, transversalState)))
      val paraphrase2 = Knowledge(lang=lang, sentence=paraphraseB, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaB, transversalState)))
      val paraphrase3 = Knowledge(lang=lang, sentence=paraphraseC, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaC, transversalState)))
      val paraphrase4 = Knowledge(lang=lang, sentence=paraphraseD, extentInfoJson = "{}", knowledgeForTables=List(uploadTable(knowledgeForTableParaD, transversalState)))

      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge4), transversalState)

      val propositionIdForInference = getUUID()
      val premiseKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase1), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase2))
      val claimKnowledge = List(KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase3), KnowledgeForParser(propositionIdForInference, getUUID(), paraphrase4))
      val inputSentenceForParser = InputSentenceForParser(premiseKnowledge, claimKnowledge, ActionModeType.DEDUCTION_MODE.index)

      val json = getAnalyzedSentenceObjectsJsonForSemiGlobal(lang,inputSentenceForParser, transversalState)
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.havePremiseInGivenProposition).size == 0)
    }
  }

}
