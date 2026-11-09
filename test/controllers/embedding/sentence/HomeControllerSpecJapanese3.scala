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

package controllers.embedding.sentence

import org.apache.pekko.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{SentenceType, SuperiorType, NonSentenceType, CaseGroupType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorId, FeatureVectorIdentifier}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, InputSentenceForParser, KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import controllers.TestUtilsEx.{deleteFeatureVector, deleteNeo4JAllData, registerSingleClaim}
//import io.jvm.uuid.UUID
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test.{FakeRequest, _}

import scala.concurrent.duration.DurationInt
import com.ideal.linked.toposoid.common.ActionModeType
import com.ideal.linked.toposoid.protocol.model.frontend.Endpoint
import controllers.HomeController
import controllers.TestUtilsEx
import com.ideal.linked.toposoid.common.InMemoryDbUtils
import com.ideal.linked.toposoid.common.DeductionPhaseType
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForImage
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import controllers.TestUtilsEx.deleteFeatureVectorByPropositionId
import com.ideal.linked.toposoid.common.AuthenticityType

class HomeControllerSpecJapanese3 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite  with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    deleteNeo4JAllData(transversalState)
    Thread.sleep(5000)
  }

  override def beforeAll(): Unit = {
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    TestUtils.setDeductionUnitEndPoints(DeductionPhaseType.DEDUCTION_SENTENCE_BASE, transversalState, selectIndice = List(0))
    deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    deleteNeo4JAllData(transversalState)
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]

  val sentenceA = "自然界の法則がすべての慣性系で同じように成り立っている。"
  val sentenceB = "どの慣性系から見ても光の速さは一定である。"
  val sentenceC = "運動する物体の速さの上限は光の速さである。"
  val sentenceD = "特殊相対性理論では運動する物体の時間は進みかたが遅くなる。"

  val sentenceNotA = "自然界の法則がすべての慣性系で同じように成り立っていない。"
  val sentenceNotB = "どの慣性系から見ても光の速さは一定ではない。"
  val sentenceNotC = "運動する物体の速さの上限は光の速さではない。"
  val sentenceNotD = "特殊相対性理論では運動する物体の時間は進みかたが遅くならない。"

  val paraphraseA = "自然界の物理法則は例外なくどの慣性系でも成立する。"
  val paraphraseB = "見ている慣性系によらず光速は不変である。"
  val paraphraseC = "物体の運動する速さは光の速さ未満である。"
  val paraphraseD = "特殊相対性理論において物体は運動することにより時間がゆっくり進む。"

  val lang = "ja_JP"

  "The specification21" should {
    "returns an appropriate response" in {

      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId, transversalState)      
    }
  }

  "The specification22" should {
    "returns an appropriate response" in {

      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)      
    }
  }

  "The specification23" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)      
    }
  }

  "The specification24" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)      
    }
  }

  "The specification25" should {
    "returns an appropriate response" in {
      val propositionId = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId, transversalState)      
      
    }
  }

  "The specification26" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)            
    }
  }

  "The specification27" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)            
    }
  }

  "The specification28" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)            
    }
  }
  "The specification29" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)            
    }
  }

  "The specification30" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            
      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)            
    }
  }

  "The specification31" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState)            
    }
  }

  "The specification32" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState)            
    }
  }

  "The specification33" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState)            
    }
  }

  "The specification34" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.INSUFFICIENT.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState)            
    }
  }

  "The specification35" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val propositionId4 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState) 
      deleteFeatureVectorByPropositionId(propositionId4, transversalState)            
    }
  }

  "The specification36" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val propositionId4 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState) 
      deleteFeatureVectorByPropositionId(propositionId4, transversalState)            
    }
  }
  
  "The specification37" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val propositionId4 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.SUPPORTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState) 
      deleteFeatureVectorByPropositionId(propositionId4, transversalState)            
    }
  }

  "The specification38" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val propositionId4 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState) 
      deleteFeatureVectorByPropositionId(propositionId4, transversalState)            
    }
  }

  "The specification39" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val propositionId4 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState) 
      deleteFeatureVectorByPropositionId(propositionId4, transversalState)            
    }
  }

  "The specification40" should {
    "returns an appropriate response" in {
      val propositionId1 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )      
      val propositionId2 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        premises = List((sentenceNotD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((sentenceNotB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val propositionId3 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceC, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val propositionId4 = TestUtilsEx.registerKnowledge(
        lang =lang,
        transversalState=transversalState, 
        claims = List((sentenceNotD, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )            

      val json = TestUtilsEx.prepairInputJson(
        lang = lang, 
        transversalState =transversalState, 
        premises = List((paraphraseA, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable])),
        claims = List((paraphraseB, List.empty[KnowledgeForImage], List.empty[KnowledgeForTable]))
      )
      val fr = FakeRequest(POST, "/execute")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.execute(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.PREMISE.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType.equals(SentenceType.CLAIM.index)  && x.deductionResult.authenticityType == AuthenticityType.REFUTED.index ).size == 1)
      deleteFeatureVectorByPropositionId(propositionId1, transversalState)      
      deleteFeatureVectorByPropositionId(propositionId2, transversalState)   
      deleteFeatureVectorByPropositionId(propositionId3, transversalState) 
      deleteFeatureVectorByPropositionId(propositionId4, transversalState)            
    }
  }
}
