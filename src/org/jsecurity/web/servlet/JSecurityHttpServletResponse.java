/*
 * Copyright (C) 2005-2007 Les Hazlewood
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the
 *
 * Free Software Foundation, Inc.
 * 59 Temple Place, Suite 330
 * Boston, MA 02111-1307
 * USA
 *
 * Or, you may view it online at
 * http://www.opensource.org/licenses/lgpl-license.php
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jsecurity.web.servlet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * TODO class JavaDoc
 * <p>Note that this implementation relies in part on source code from the Tomcat 6.x distribution for
 * encoding URLs for session ID URL Rewriting (we didn't want to re-invent the wheel).  As such, it is dual licensed
 * under both the LGPL and Apache 2.0 license to conform to and respect the original authors' licensing intent.
 *
 * @author Les Hazlewood
 * @since 0.2
 */
public class JSecurityHttpServletResponse extends HttpServletResponseWrapper {

    private ServletContext context = null;
    //the associated request
    private JSecurityHttpServletRequest request = null;

    public JSecurityHttpServletResponse( HttpServletResponse wrapped, ServletContext context, JSecurityHttpServletRequest request ) {
        super( wrapped );
        this.context = context;
        this.request = request;
    }

    public ServletContext getContext() {
        return context;
    }

    public void setContext( ServletContext context ) {
        this.context = context;
    }

    public JSecurityHttpServletRequest getRequest() {
        return request;
    }

    public void setRequest( JSecurityHttpServletRequest request ) {
        this.request = request;
    }

    /**
     * Encode the session identifier associated with this response
     * into the specified redirect URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeRedirectURL( String url ) {
        if ( isEncodeable( toAbsolute( url ) ) ) {
            return toEncoded( url, request.getSession().getId() );
        } else {
            return url;
        }
    }


    public String encodeRedirectUrl( String s ) {
        return encodeRedirectURL( s );
    }


    /**
     * Encode the session identifier associated with this response
     * into the specified URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeURL( String url ) {
        String absolute = toAbsolute( url );
        if ( isEncodeable( absolute ) ) {
            // W3c spec clearly said
            if ( url.equalsIgnoreCase( "" ) ) {
                url = absolute;
            }
            return toEncoded( url, request.getSession().getId() );
        } else {
            return url;
        }
    }

    public String encodeUrl( String s ) {
        return encodeURL( s );
    }

    /**
     * Return <code>true</code> if the specified URL should be encoded with
     * a session identifier.  This will be true if all of the following
     * conditions are met:
     * <ul>
     * <li>The request we are responding to asked for a valid session
     * <li>The requested session ID was not received via a cookie
     * <li>The specified URL points back to somewhere within the web
     * application that is responding to this request
     * </ul>
     *
     * @param location Absolute URL to be validated
     */
    protected boolean isEncodeable( final String location ) {

        if ( location == null )
            return ( false );

        // Is this an intra-document reference?
        if ( location.startsWith( "#" ) )
            return ( false );

        // Are we in a valid session that is not using cookies?
        final HttpServletRequest hreq = request;
        final HttpSession session = hreq.getSession( false );
        if ( session == null )
            return ( false );
        if ( hreq.isRequestedSessionIdFromCookie() )
            return ( false );

        return doIsEncodeable( hreq, session, location );
    }

    private boolean doIsEncodeable( HttpServletRequest hreq, HttpSession session, String location ) {
        // Is this a valid absolute URL?
        URL url = null;
        try {
            url = new URL( location );
        } catch ( MalformedURLException e ) {
            return ( false );
        }

        // Does this URL match down to (and including) the context path?
        if ( !hreq.getScheme().equalsIgnoreCase( url.getProtocol() ) )
            return ( false );
        if ( !hreq.getServerName().equalsIgnoreCase( url.getHost() ) )
            return ( false );
        int serverPort = hreq.getServerPort();
        if ( serverPort == -1 ) {
            if ( "https".equals( hreq.getScheme() ) )
                serverPort = 443;
            else
                serverPort = 80;
        }
        int urlPort = url.getPort();
        if ( urlPort == -1 ) {
            if ( "https".equals( url.getProtocol() ) )
                urlPort = 443;
            else
                urlPort = 80;
        }
        if ( serverPort != urlPort )
            return ( false );

        String contextPath = getRequest().getContextPath();
        if ( contextPath != null ) {
            String file = url.getFile();
            if ( ( file == null ) || !file.startsWith( contextPath ) )
                return ( false );
            String tok = ";" + JSecurityHttpSession.DEFAULT_SESSION_ID_NAME + "=" + session.getId();
            if ( file.indexOf( tok, contextPath.length() ) >= 0 )
                return ( false );
        }

        // This URL belongs to our web application, so it is encodeable
        return ( true );

    }


    /**
     * Convert (if necessary) and return the absolute URL that represents the
     * resource referenced by this possibly relative URL.  If this URL is
     * already absolute, return it unchanged.
     *
     * @param location URL to be (possibly) converted and then returned
     * @throws IllegalArgumentException if a MalformedURLException is
     *                                  thrown when converting the relative URL to an absolute one
     */
    private String toAbsolute( String location ) {

        if ( location == null )
            return ( location );

        boolean leadingSlash = location.startsWith( "/" );

        if ( leadingSlash || !hasScheme( location ) ) {

            StringBuffer buf = new StringBuffer();

            String scheme = request.getScheme();
            String name = request.getServerName();
            int port = request.getServerPort();

            try {
                buf.append( scheme ).append( "://" ).append( name );
                if ( ( scheme.equals( "http" ) && port != 80 )
                    || ( scheme.equals( "https" ) && port != 443 ) ) {
                    buf.append( ':' ).append( port );
                }
                if ( !leadingSlash ) {
                    String relativePath = request.getRequestURI();
                    int pos = relativePath.lastIndexOf( '/' );
                    relativePath = relativePath.substring( 0, pos );

                    String encodedURI = URLEncoder.encode( relativePath, getCharacterEncoding() );
                    buf.append( encodedURI ).append( '/' );
                }
                buf.append( location );
            } catch ( IOException e ) {
                IllegalArgumentException iae = new IllegalArgumentException( location );
                iae.initCause( e );
                throw iae;
            }

            return buf.toString();

        } else {
            return location;
        }
    }

    /**
     * Determine if the character is allowed in the scheme of a URI.
     * See RFC 2396, Section 3.1
     */
    public static boolean isSchemeChar( char c ) {
        return Character.isLetterOrDigit( c ) ||
            c == '+' || c == '-' || c == '.';
    }


    /**
     * Determine if a URI string has a <code>scheme</code> component.
     */
    private boolean hasScheme( String uri ) {
        int len = uri.length();
        for ( int i = 0; i < len; i++ ) {
            char c = uri.charAt( i );
            if ( c == ':' ) {
                return i > 0;
            } else if ( !isSchemeChar( c ) ) {
                return false;
            }
        }
        return false;
    }

    /**
     * Return the specified URL with the specified session identifier
     * suitably encoded.
     *
     * @param url       URL to be encoded with the session id
     * @param sessionId Session id to be included in the encoded URL
     */
    protected String toEncoded( String url, String sessionId ) {

        if ( ( url == null ) || ( sessionId == null ) )
            return ( url );

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf( '?' );
        if ( question >= 0 ) {
            path = url.substring( 0, question );
            query = url.substring( question );
        }
        int pound = path.indexOf( '#' );
        if ( pound >= 0 ) {
            anchor = path.substring( pound );
            path = path.substring( 0, pound );
        }
        StringBuffer sb = new StringBuffer( path );
        if ( sb.length() > 0 ) { // jsessionid can't be first.
            sb.append( ";" );
            sb.append( JSecurityHttpSession.DEFAULT_SESSION_ID_NAME );
            sb.append( "=" );
            sb.append( sessionId );
        }
        sb.append( anchor );
        sb.append( query );
        return ( sb.toString() );

    }
}
