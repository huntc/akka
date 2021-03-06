/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package parser

import akka.parboiled2.Parser
import headers._

/**
 * Parser rules for all headers that can be parsed with one single rule.
 * All header rules that require more than one single rule are modelled in their own trait.
 */
private[parser] trait SimpleHeaders { this: Parser with CommonRules with CommonActions with IpAddressParsing ⇒

  // http://tools.ietf.org/html/rfc7233#section-2.3
  def `accept-ranges` = rule {
    ("none" ~ push(Nil) | zeroOrMore(ws(',')) ~ oneOrMore(`range-unit`).separatedBy(listSep)) ~ EOI ~> (`Accept-Ranges`(_: _*))
  }

  // http://www.w3.org/TR/cors/#access-control-allow-credentials-response-header
  // in addition to the spec we also allow for a `false` value
  def `access-control-allow-credentials` = rule(
    ("true" ~ push(`Access-Control-Allow-Credentials`(true))
      | "false" ~ push(`Access-Control-Allow-Credentials`(false))) ~ EOI)

  // http://www.w3.org/TR/cors/#access-control-allow-headers-response-header
  def `access-control-allow-headers` = rule {
    zeroOrMore(token).separatedBy(listSep) ~ EOI ~> (`Access-Control-Allow-Headers`(_: _*))
  }

  // http://www.w3.org/TR/cors/#access-control-allow-methods-response-header
  def `access-control-allow-methods` = rule {
    zeroOrMore(httpMethodDef).separatedBy(listSep) ~ EOI ~> (`Access-Control-Allow-Methods`(_: _*))
  }

  // http://www.w3.org/TR/cors/#access-control-allow-origin-response-header
  def `access-control-allow-origin` = rule {
    (ws('*') ~ push(HttpOriginRange.`*`) | `origin-list-or-null` ~> (HttpOriginRange(_: _*))) ~ EOI ~>
      (`Access-Control-Allow-Origin`(_))
  }

  // http://www.w3.org/TR/cors/#access-control-expose-headers-response-header
  def `access-control-expose-headers` = rule {
    zeroOrMore(token).separatedBy(listSep) ~ EOI ~> (`Access-Control-Expose-Headers`(_: _*))
  }

  // http://www.w3.org/TR/cors/#access-control-max-age-response-header
  def `access-control-max-age` = rule {
    `delta-seconds` ~ EOI ~> (`Access-Control-Max-Age`(_))
  }

  // http://www.w3.org/TR/cors/#access-control-request-headers-request-header
  def `access-control-request-headers` = rule {
    zeroOrMore(token).separatedBy(listSep) ~ EOI ~> (`Access-Control-Request-Headers`(_: _*))
  }

  // http://www.w3.org/TR/cors/#access-control-request-method-request-header
  def `access-control-request-method` = rule {
    httpMethodDef ~ EOI ~> (`Access-Control-Request-Method`(_))
  }

  // http://tools.ietf.org/html/rfc7231#section-7.4.1
  def allow = rule {
    zeroOrMore(httpMethodDef).separatedBy(listSep) ~ EOI ~> (Allow(_: _*))
  }

  // http://tools.ietf.org/html/rfc7235#section-4.2
  def authorization = rule { credentials ~ EOI ~> (Authorization(_)) }

  // http://tools.ietf.org/html/rfc7230#section-6.1
  def connection = rule {
    oneOrMore(token).separatedBy(listSep) ~ EOI ~> (Connection(_))
  }

  // http://tools.ietf.org/html/rfc7231#section-3.1.2.2
  // http://tools.ietf.org/html/rfc7231#appendix-D
  def `content-encoding` = rule {
    oneOrMore(token ~> (x ⇒ HttpEncodings.getForKey(x.toLowerCase) getOrElse HttpEncoding.custom(x)))
      .separatedBy(listSep) ~ EOI ~> (`Content-Encoding`(_))
  }

  // http://tools.ietf.org/html/rfc7230#section-3.3.2
  def `content-length` = rule {
    longNumberCapped ~> (`Content-Length`(_)) ~ EOI
  }

  // http://tools.ietf.org/html/rfc7233#section-4.2
  def `content-range` = rule {
    (`byte-content-range` | `other-content-range`) ~ EOI ~> (`Content-Range`(_, _))
  }

  // https://tools.ietf.org/html/rfc6265#section-4.2
  def `cookie` = rule {
    oneOrMore(`cookie-pair`).separatedBy(ws(';')) ~ EOI ~> (`Cookie`(_))
  }

  // http://tools.ietf.org/html/rfc7231#section-7.1.1.2
  def `date` = rule {
    `HTTP-date` ~ EOI ~> (Date(_))
  }

  // http://tools.ietf.org/html/rfc7232#section-2.3
  def etag = rule { `entity-tag` ~ EOI ~> (ETag(_)) }

  // http://tools.ietf.org/html/rfc7231#section-5.1.1
  def `expect` = rule {
    ignoreCase("100-continue") ~ OWS ~ push(Expect.`100-continue`)
  }

  // http://tools.ietf.org/html/rfc7230#section-5.4
  // We don't accept scoped IPv6 addresses as they should not appear in the Host header,
  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
  // Also: an empty hostnames with a non-empty port value (as in `Host: :8080`) are *allowed*,
  // see http://trac.tools.ietf.org/wg/httpbis/trac/ticket/92
  def host = rule {
    runSubParser(new UriParser(_).`hostAndPort-pushed`) ~ EOI ~> (Host(_, _))
  }

  // http://tools.ietf.org/html/rfc7232#section-3.1
  def `if-match` = rule(
    ws('*') ~ push(`If-Match`.`*`)
      | oneOrMore(`entity-tag`).separatedBy(listSep) ~ EOI ~> (tags ⇒ `If-Match`(EntityTagRange(tags: _*))))

  // http://tools.ietf.org/html/rfc7232#section-3.3
  def `if-modified-since` = rule { `HTTP-date` ~ EOI ~> (`If-Modified-Since`(_)) }

  // http://tools.ietf.org/html/rfc7232#section-3.2
  def `if-none-match` = rule {
    ws('*') ~ push(`If-None-Match`.`*`) |
      oneOrMore(`entity-tag`).separatedBy(listSep) ~ EOI ~> (tags ⇒ `If-None-Match`(EntityTagRange(tags: _*)))
  }

  // http://tools.ietf.org/html/rfc7232#section-3.5
  // http://tools.ietf.org/html/rfc7233#section-3.2
  def `if-range` = rule { (`entity-tag` ~> (Left(_)) | `HTTP-date` ~> (Right(_))) ~ EOI ~> (`If-Range`(_)) }

  // http://tools.ietf.org/html/rfc7232#section-3.4
  def `if-unmodified-since` = rule { `HTTP-date` ~ EOI ~> (`If-Unmodified-Since`(_)) }

  // http://tools.ietf.org/html/rfc7232#section-2.2
  def `last-modified` = rule { `HTTP-date` ~ EOI ~> (`Last-Modified`(_)) }

  // http://tools.ietf.org/html/rfc7231#section-7.1.2
  def location = rule {
    runSubParser(new UriParser(_).`URI-reference-pushed`) ~ EOI ~> (Location(_))
  }

  // http://tools.ietf.org/html/rfc6454#section-7
  def `origin` = rule { `origin-list-or-null` ~ EOI ~> (Origin(_)) }

  // http://tools.ietf.org/html/rfc7235#section-4.3
  def `proxy-authenticate` = rule {
    oneOrMore(challenge).separatedBy(listSep) ~ EOI ~> (`Proxy-Authenticate`(_))
  }

  // http://tools.ietf.org/html/rfc7235#section-4.4
  def `proxy-authorization` = rule { credentials ~ EOI ~> (`Proxy-Authorization`(_)) }

  // http://tools.ietf.org/html/rfc7233#section-3.1
  def `range` = rule { `byte-ranges-specifier` /*| `other-ranges-specifier` */ ~ EOI ~> (Range(_, _)) }

  // http://tools.ietf.org/html/rfc7231#section-7.4.2
  def server = rule { products ~> (Server(_)) }

  // http://tools.ietf.org/html/rfc7230#section-3.3.1
  def `transfer-encoding` = rule {
    oneOrMore(`transfer-coding`).separatedBy(listSep) ~ EOI ~> (`Transfer-Encoding`(_))
  }

  // https://tools.ietf.org/html/rfc6265
  def `set-cookie` = rule {
    `cookie-pair` ~ zeroOrMore(ws(';') ~ `cookie-av`) ~ EOI ~> (`Set-Cookie`(_))
  }

  // http://tools.ietf.org/html/rfc7231#section-5.5.3
  def `user-agent` = rule { products ~> (`User-Agent`(_)) }

  // http://tools.ietf.org/html/rfc7235#section-4.1
  def `www-authenticate` = rule {
    oneOrMore(challenge).separatedBy(listSep) ~ EOI ~> (`WWW-Authenticate`(_))
  }

  // de-facto standard as per http://en.wikipedia.org/wiki/X-Forwarded-For
  // It's not clear in which format IpV6 addresses are to be expected, the ones we've seen in the wild
  // were not quoted and that's also what the "Transition" section in the draft says:
  // http://tools.ietf.org/html/draft-ietf-appsawg-http-forwarded-10
  def `x-forwarded-for` = {
    def addr = rule { (`ip-v4-address` | `ip-v6-address`) ~> (RemoteAddress(_)) | "unknown" ~ push(RemoteAddress.Unknown) }
    rule { oneOrMore(addr).separatedBy(listSep) ~ EOI ~> (`X-Forwarded-For`(_)) }
  }
}