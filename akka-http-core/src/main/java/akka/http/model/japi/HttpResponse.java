/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model.japi;

/**
 * Represents an Http response.
 */
public abstract class HttpResponse implements HttpMessage, HttpMessage.MessageTransformations<HttpResponse> {
    /**
     * Returns the status-code of this response.
     */
    public abstract StatusCode status();

    /**
     * Returns a copy of this instance with a new status-code.
     */
    public abstract HttpResponse withStatus(StatusCode statusCode);

    /**
     * Returns a copy of this instance with a new status-code.
     */
    public abstract HttpResponse withStatus(int statusCode);

    /**
     * Returns a default response to be changed using the `withX` methods.
     */
    public static HttpResponse create() {
        return Accessors$.MODULE$.HttpResponse();
    }
}
