/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.notifications.filters;

import com.evolveum.midpoint.common.expression.Expression;
import com.evolveum.midpoint.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.common.expression.ExpressionFactory;
import com.evolveum.midpoint.notifications.NotificationManager;
import com.evolveum.midpoint.notifications.events.Event;
import com.evolveum.midpoint.notifications.handlers.BaseHandler;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.EventExpressionFilterType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.EventHandlerType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mederly
 */
@Component
public class ExpressionFilter extends BaseHandler {

    private static final Trace LOGGER = TraceManager.getTrace(ExpressionFilter.class);


    @PostConstruct
    public void init() {
        register(EventExpressionFilterType.class);
    }

    @Override
    public boolean processEvent(Event event, EventHandlerType eventHandlerType, NotificationManager notificationManager, OperationResult result) {

        EventExpressionFilterType eventExpressionFilterType = (EventExpressionFilterType) eventHandlerType;
        ExpressionType expressionType = eventExpressionFilterType.getExpression();

        logStart(LOGGER, event, eventHandlerType, expressionType);

        boolean retval = evaluateBooleanExpressionChecked(expressionType, getDefaultVariables(event), "event filter expression", result);

        logEnd(LOGGER, event, eventHandlerType, retval);
        return retval;
    }

}
