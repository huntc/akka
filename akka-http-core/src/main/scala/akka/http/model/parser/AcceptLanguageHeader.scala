/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package parser

import akka.parboiled2.Parser
import headers._

private[parser] trait AcceptLanguageHeader { this: Parser with CommonRules with CommonActions ⇒

  // http://tools.ietf.org/html/rfc7231#section-5.3.5
  def `accept-language` = rule {
    oneOrMore(`language-range-decl`).separatedBy(listSep) ~ EOI ~> (`Accept-Language`(_))
  }

  def `language-range-decl` = rule {
    `language-range` ~ optional(weight) ~> { (range, optQ) ⇒
      optQ match {
        case None    ⇒ range
        case Some(q) ⇒ range withQValue q
      }
    }
  }

  def `language-range` = rule { ws('*') ~ push(LanguageRange.`*`) | language }
}