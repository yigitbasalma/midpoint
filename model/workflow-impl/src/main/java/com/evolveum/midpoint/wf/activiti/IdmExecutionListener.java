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

package com.evolveum.midpoint.wf.activiti;

import javax.xml.bind.JAXBElement;

import org.activiti.engine.delegate.ExecutionListener;
import org.activiti.engine.impl.pvm.delegate.ExecutionListenerExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolveum.midpoint.xml.ns._public.communication.workflow_1.ObjectFactory;
import com.evolveum.midpoint.xml.ns._public.communication.workflow_1.WfProcessInstanceEventType;
import com.evolveum.midpoint.xml.ns._public.communication.workflow_1.WfProcessInstanceFinishedEventType;
import com.evolveum.midpoint.xml.ns._public.communication.workflow_1.WfProcessInstanceStartedEventType;
import com.evolveum.midpoint.xml.ns._public.communication.workflow_1.WfProcessVariable;

/**
 * Currently unused. Necessary for "smart" workflow tasks.
 */

public class IdmExecutionListener {
	
	private static Logger logger = LoggerFactory.getLogger(IdmExecutionListener.class);

	public void notify(ExecutionListenerExecution execution) throws Exception {
		notify(execution, "", null);
	}

	public void notify(ExecutionListenerExecution execution, String description) throws Exception {
		notify(execution, "", null);
	}
	
	public void notify(ExecutionListenerExecution execution, String description, String wfAnswer) throws Exception {

		if (execution == null)
		{
			logger.error("Variable 'execution' (ExecutionListenerExecution) cannot be null, please check your listener configuration.");
			return;
		}
		
		String eventName = execution.getEventName();
		String pid = execution.getProcessInstanceId();
		Object midpointTaskOid = execution.getVariable("midpointTaskOid");
		if (wfAnswer == null)
			wfAnswer = (String) execution.getVariable("wfAnswer");

        if (logger.isTraceEnabled()) {
		    logger.trace("** PROCESS EXECUTION EVENT: " + eventName + " **");
		    logger.trace("Process instance id = " + pid);
		    logger.trace("Description = " + description);
		    logger.trace("Answer = " + wfAnswer);
        }
		
		WfProcessInstanceEventType event;
				
		if (ExecutionListener.EVENTNAME_START.equals(eventName))
			event = new WfProcessInstanceStartedEventType();
		else if (ExecutionListener.EVENTNAME_END.equals(eventName))
			event = new WfProcessInstanceFinishedEventType();
		else 
			event = new WfProcessInstanceEventType();
			
		event.setWfProcessInstanceId(pid);
		event.setWfAnswer(wfAnswer);
		
		if (midpointTaskOid != null)
			event.setMidpointTaskOid(midpointTaskOid.toString());
		else
			logger.error("Process variable midpointTaskOid is not set!");
		
		event.setWfStateDescription(description);
		
		for (String v : execution.getVariableNames())
		{
            if (logger.isTraceEnabled()) {
			    logger.trace("Variable " + v + " = " + execution.getVariable(v));
            }
			WfProcessVariable pv = new WfProcessVariable();
			pv.setName(v);
			Object o = execution.getVariable(v);
			pv.setValue(o != null ? o.toString() : null);
			event.getWfProcessVariable().add(pv);
		}
        if (logger.isTraceEnabled()) {
		    logger.trace("(end of event data, sending camel message)");
        }
		
		//producerToIdm.sendBody(marshal(event));
        if (logger.isTraceEnabled()) {
		    logger.trace("(camel message sent)");
        }
	}
	
	public void notify(WfProcessInstanceEventType event) throws Exception
	{
		//producerToIdm.sendBody(marshal(event));
        if (logger.isTraceEnabled()) {
		    logger.trace("(custom camel message sent)");
        }
	}

	private String marshal(WfProcessInstanceEventType event) throws Exception
	{
		ObjectFactory of = new ObjectFactory();
		JAXBElement<?> jaxbElement;
		if (event instanceof WfProcessInstanceStartedEventType)
			jaxbElement = of.createWfProcessInstanceStartedEvent((WfProcessInstanceStartedEventType) event);
		else if (event instanceof WfProcessInstanceFinishedEventType)
			jaxbElement = of.createWfProcessInstanceFinishedEvent((WfProcessInstanceFinishedEventType) event);
		else
			jaxbElement = of.createWfProcessInstanceEvent(event);
		
		return //JAXBUtil.marshal(jaxbElement);
        null;
	}
}
