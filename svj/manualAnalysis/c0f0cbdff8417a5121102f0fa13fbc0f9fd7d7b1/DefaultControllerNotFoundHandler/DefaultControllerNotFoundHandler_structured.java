/***
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.caelum.vraptor.controller;

import java.io.IOException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.ServletException;
import br.com.caelum.vraptor.InterceptionException;
import br.com.caelum.vraptor.core.RequestInfo;
import br.com.caelum.vraptor.events.ControllerNotFound;

/**
 * Default 404 component. It defers the request back to container
 *
 * @author Lucas Cavalcanti
 * @author Luiz Real
 */
@ApplicationScoped
public class DefaultControllerNotFoundHandler implements ControllerNotFoundHandler {

    private final Event<ControllerNotFound> event;

    /**
	 * @deprecated CDI eyes only
	 */
    DefaultControllerNotFoundHandler() {
        this(null);
    }

    @Inject
    public DefaultControllerNotFoundHandler(Event<ControllerNotFound> event) {
        this.event = event;
    }

    @Override
    public void couldntFind(RequestInfo request) {
        event.fire(new ControllerNotFound());
        try {
            request.getChain().doFilter(request.getRequest(), request.getResponse());
        } catch (IOExceptionServletException |  e) {
            throw new InterceptionException(e);
        }
    }
}

