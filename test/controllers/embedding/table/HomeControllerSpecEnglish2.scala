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
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import com.ideal.linked.toposoid.common.AuthenticityType


class HomeControllerSpecEnglish2 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    deleteNeo4JAllData(transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_TABLE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    setDeductionUnitEndPoints(DeductionPhaseType.DEDUCTION_SENTENCE_BASE, transversalState, selectIndice = List(0,2))
    deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    deleteNeo4JAllData(transversalState)
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]
  val sentenceA = "The experimental data suggested a perfect match with the theoretical values up to 18 decimal places."
  val sentenceNotA = "The experimental data does not suggest a perfect match with the theoretical values up to 18 decimal places."
  val referenceA = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table1.xlsx")
  val tableReferenceA = TableReference(referenceA, skipHeaderRows=5, skipRowList=List(),multiHeaderRows=4, sheetNameForExcel= "se0101")
  val knowledgeForTableA = KnowledgeForTable(getUUID(), tableReferenceA)  

  val sentenceB = "The server well-suited for data-driven processing is used for storing and processing large amounts of marketing data for discovering potential customers."
  val sentenceNotB = "The server well-suited for data-driven processing is not used for storing and processing large amounts of marketing data for discovering potential customers."
  val referenceB = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table2.csv")
  val tableReferenceB = TableReference(referenceB, skipHeaderRows=8, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")  
  val knowledgeForTableB = KnowledgeForTable(getUUID(), tableReferenceB)    
  
  val sentenceC = "Evidence data1 is inevitably required."
  val referenceC = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table3.xls")
  val tableReferenceC = TableReference(referenceC, skipHeaderRows=3, skipRowList=List(),multiHeaderRows=3, sheetNameForExcel= "")  
  val knowledgeForTableC = KnowledgeForTable(getUUID(), tableReferenceC)  

  val sentenceD = "It depends on the evidence data."  
  val referenceD = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table4.xlsx")
  val tableReferenceD = TableReference(referenceD, skipHeaderRows=2, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")  
  val knowledgeForTableD = KnowledgeForTable(getUUID(), tableReferenceD)  

  val paraphraseA = "The experimental sample suggested a perfect match with the theoretical values up to 18 decimal places."
  val referenceParaA = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table1.xlsx")
  val tableReferenceParaA = TableReference(referenceParaA, skipHeaderRows=5, skipRowList=List(),multiHeaderRows=4, sheetNameForExcel= "se0101")
  val knowledgeForTableParaA = KnowledgeForTable(getUUID(), tableReferenceParaA)

  val paraphraseB = "The server well-suited for data-driven processing is used for storing and processing large amounts of marketing sample for discovering potential customers."
  val referenceParaB = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table2.csv")
  val tableReferenceParaB = TableReference(referenceParaB, skipHeaderRows=8, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")
  val knowledgeForTableParaB = KnowledgeForTable(getUUID(), tableReferenceParaB)  

  val paraphraseC = "Evidence sample1 is inevitably required."
  val referenceParaC = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table3.xls")
  val tableReferenceParaC = TableReference(referenceParaC, skipHeaderRows=3, skipRowList=List(),multiHeaderRows=3, sheetNameForExcel= "")
  val knowledgeForTableParaC = KnowledgeForTable(getUUID(), tableReferenceParaC)  

  val paraphraseD = "It depends on the evidence sample."
  val referenceParaD = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "/app/toposoid-embedding-deduction-unit-common-web/test/data/tables/test_table4.xlsx")
  val tableReferenceParaD = TableReference(referenceParaD, skipHeaderRows=2, skipRowList=List(),multiHeaderRows=1, sheetNameForExcel= "")
  val knowledgeForTableParaD = KnowledgeForTable(getUUID(), tableReferenceParaD)   

  val lang = "en_US"

  "The specification1" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceA, List.empty[KnowledgeForImage], List(knowledgeForTableA)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotA, List.empty[KnowledgeForImage], List(knowledgeForTableA)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceA, List.empty[KnowledgeForImage], List(knowledgeForTableC)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

  "The specification4" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List(knowledgeForTableC)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

   "The specification5" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceB, List.empty[KnowledgeForImage], List(knowledgeForTableB)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

  "The specification6" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotB, List.empty[KnowledgeForImage], List(knowledgeForTableB)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

  "The specification7" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceB, List.empty[KnowledgeForImage], List(knowledgeForTableC)))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  }

  "The specification8" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceA, List.empty[KnowledgeForImage], List(knowledgeForTableA)))
      )            
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceB, List.empty[KnowledgeForImage], List(knowledgeForTableB)))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List(knowledgeForTableParaA))),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List(knowledgeForTableParaB)))
      )      
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index) && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index) && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)      
      //deleteFeatureVectorByPropositionId(propositionId, transversalState)
    }
  } 
}
