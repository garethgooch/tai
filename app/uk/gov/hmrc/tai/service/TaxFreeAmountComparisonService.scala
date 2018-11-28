/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.tai.service

import com.google.inject.Inject
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.tai.connectors.TaxAccountHistoryConnector

import scala.concurrent.Future
import scala.util.{Failure, Success}

final case class TaxFreeAmountComparison(previous: Seq[CodingComponent], next: Seq[CodingComponent])

class TaxFreeAmountComparisonService @Inject()(taxCodeChangeService: TaxCodeChangeService,
                                               codingComponentService: CodingComponentService,
                                               taxAccountHistoryConnector: TaxAccountHistoryConnector) {

  def taxFreeAmountComparison(nino: Nino)(implicit hc:HeaderCarrier): Future[TaxFreeAmountComparison] = {

    val currentComponents = codingComponentService.codingComponents(nino, TaxYear())


    for {
      current <- currentComponents
    } yield {
      TaxFreeAmountComparison(Seq.empty, current)
    }
  }

  def previousTaxCodeChangeIds(nino: Nino)(implicit hc:HeaderCarrier): Future[Seq[Int]] = {
    val taxCodeChange = taxCodeChangeService.taxCodeChange(nino)
    taxCodeChange.map(_.previous.map(_.taxCodeId))
  }

  def buildPreviousCodingComponentsFromIds(nino: Nino, taxCodeIds: Seq[Int])(implicit hc:HeaderCarrier): Future[Seq[CodingComponent]] = {
    Future.sequence(taxCodeIds.map(id => {
      val response: Future[Seq[CodingComponent]] = codingComponentsForId(nino, id)

      response
    })).map(_.flatten).recoverWith {
      case e: Exception => Future.failed(new RuntimeException("Could not retrieve all previous coding components - " + e.getMessage))
    }
  }

  def codingComponentsForId(nino: Nino, id: Int)(implicit hc:HeaderCarrier): Future[Seq[CodingComponent]] = {
    taxAccountHistoryConnector.taxAccountHistory(nino, id).map {
      case Success(components: Seq[CodingComponent]) => components
      case Failure(exception) => throw exception
    }
  }
}
