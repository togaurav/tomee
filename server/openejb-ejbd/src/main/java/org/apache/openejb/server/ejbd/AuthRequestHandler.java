/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.server.ejbd;

import org.apache.openejb.client.AuthenticationRequest;
import org.apache.openejb.client.AuthenticationResponse;
import org.apache.openejb.client.ClientMetaData;
import org.apache.openejb.client.ResponseCodes;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.spi.SecurityService;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;
import org.apache.openejb.util.Messages;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class AuthRequestHandler {

    Messages _messages = new Messages("org.apache.openejb.server.util.resources");
    private static final Logger logger = Logger.getInstance(LogCategory.OPENEJB_SERVER_REMOTE.createChild("auth"), "org.apache.openejb.server.util.resources");

    AuthRequestHandler(final EjbDaemon daemon) {
    }

    public void processRequest(final ObjectInputStream in, final ObjectOutputStream out) {
        final AuthenticationRequest req = new AuthenticationRequest();
        final AuthenticationResponse res = new AuthenticationResponse();

        try {
            req.readExternal(in);

            final String securityRealm = req.getRealm();
            final String username = req.getUsername();
            final String password = req.getCredentials();

            final SecurityService securityService = SystemInstance.get().getComponent(SecurityService.class);
            final Object token = securityService.login(securityRealm, username, password);

            final ClientMetaData client = new ClientMetaData();
            client.setClientIdentity(token);

            res.setIdentity(client);
            res.setResponseCode(ResponseCodes.AUTH_GRANTED);
        } catch (Throwable t) {
            res.setResponseCode(ResponseCodes.AUTH_DENIED);
            res.setDeniedCause(t);
        } finally {
            if (logger.isDebugEnabled()) {
                try {
                    logger.debug("AUTH REQUEST: " + req + " -- RESPONSE: " + res);
                } catch (Exception justInCase) {
                }
            }

            try {
                res.writeExternal(out);
            } catch (java.io.IOException ie) {
                logger.fatal("Couldn't write AuthenticationResponse to output stream", ie);
            }
        }
    }

}