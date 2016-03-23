/*
 * Copyright (c) 2010-2014 Evolveum
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

package com.evolveum.midpoint.web.component.wizard.resource.component.synchronization;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.form.multivalue.MultiValueDropDownPanel;
import com.evolveum.midpoint.web.component.form.multivalue.MultiValueTextEditPanel;
import com.evolveum.midpoint.web.component.input.ObjectReferenceChoiceRenderer;
import com.evolveum.midpoint.web.component.input.StringChoiceRenderer;
import com.evolveum.midpoint.web.component.input.ThreeStateBooleanPanel;
import com.evolveum.midpoint.web.component.input.TriStateComboPanel;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import com.evolveum.midpoint.web.util.InfoTooltipBehavior;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  @author shood
 * */
public class SynchronizationReactionEditor extends SimplePanel<SynchronizationReactionType>{

    private static final Trace LOGGER = TraceManager.getTrace(SynchronizationReactionEditor.class);

    private static final String DOT_CLASS = SynchronizationReactionEditor.class.getName() + ".";
    private static final String OPERATION_LOAD_OBJECT_TEMPLATES = DOT_CLASS + "createObjectTemplateList";

    private static final String ID_LABEL = "label";
    private static final String ID_NAME = "name";
    private static final String ID_DESCRIPTION = "description";
    private static final String ID_SITUATION = "situation";
    private static final String ID_CHANNEL = "channel";
    private static final String ID_SYNCHRONIZE = "synchronize";
    private static final String ID_RECONCILE = "reconcile";
    private static final String ID_OBJECT_TEMPLATE_REF = "objectTemplateRef";
    private static final String ID_ACTION = "action";
    private static final String ID_ACTION_MODAL = "actionModal";
    private static final String ID_T_SITUATION = "situationTooltip";
    private static final String ID_T_CHANNEL = "channelTooltip";
    private static final String ID_T_SYNCHRONIZE = "synchronizeTooltip";
    private static final String ID_T_RECONCILE = "reconcileTooltip";
    private static final String ID_T_OBJ_TEMPLATE = "objectTemplateRefTooltip";
    private static final String ID_T_ACTION = "actionTooltip";

    private Map<String, String> objectTemplateMap = new HashMap<>();

    public SynchronizationReactionEditor(String id, IModel<SynchronizationReactionType> model){
        super(id, model);
    }

    @Override
    protected void initLayout(){
        Label label = new Label(ID_LABEL, new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                SynchronizationReactionType reaction = getModelObject();

                if(reaction.getName() == null && reaction.getSituation() == null){
                    return getString("SynchronizationReactionEditor.label.new");
                } else {
                    return getString("SynchronizationReactionEditor.label.edit",
                            reaction.getName() != null ? reaction.getName() : getString("MultiValueField.nameNotSpecified"));
                }
            }
        });
        add(label);

        TextField name = new TextField<>(ID_NAME, new PropertyModel<String>(getModel(), "name"));
        add(name);

        TextArea description = new TextArea<>(ID_DESCRIPTION, new PropertyModel<String>(getModel(), "description"));
        add(description);

        DropDownChoice situation = new DropDownChoice<>(ID_SITUATION,
                new PropertyModel<SynchronizationSituationType>(getModel(), "situation"),
                WebComponentUtil.createReadonlyModelFromEnum(SynchronizationSituationType.class),
                new EnumChoiceRenderer<SynchronizationSituationType>(this));
        situation.setNullValid(true);
        add(situation);

        MultiValueDropDownPanel channel = new MultiValueDropDownPanel<String>(ID_CHANNEL,
                new PropertyModel<List<String>>(getModel(), "channel"), true){

            @Override
            protected String createNewEmptyItem() {
                return "";
            }

            @Override
            protected IModel<List<String>> createChoiceList() {
                return new AbstractReadOnlyModel<List<String>>() {

                    @Override
                    public List<String> getObject() {
                        return WebComponentUtil.getChannelList();
                    }
                };
            }

            @Override
            protected IChoiceRenderer<String> createRenderer() {
            	return new StringChoiceRenderer("Channel.", "#");
            	
            }
        };
        add(channel);
        TriStateComboPanel synchronize = new TriStateComboPanel(ID_SYNCHRONIZE, new PropertyModel<Boolean>(getModel(), "synchronize"));

        add(synchronize);

        CheckBox reconcile = new CheckBox(ID_RECONCILE, new PropertyModel<Boolean>(getModel(), "reconcile"));
        add(reconcile);

        DropDownChoice objectTemplateRef = new DropDownChoice<>(ID_OBJECT_TEMPLATE_REF,
                new PropertyModel<ObjectReferenceType>(getModel(), "objectTemplateRef"),
                new AbstractReadOnlyModel<List<ObjectReferenceType>>() {

                    @Override
                    public List<ObjectReferenceType> getObject() {
                        return WebModelServiceUtils.createObjectReferenceList(ObjectTemplateType.class, getPageBase(), objectTemplateMap);
                    }
                }, new ObjectReferenceChoiceRenderer(objectTemplateMap));
        objectTemplateRef.setNullValid(true);
        add(objectTemplateRef);

        MultiValueTextEditPanel action = new MultiValueTextEditPanel<SynchronizationActionType>(ID_ACTION,
                new PropertyModel<List<SynchronizationActionType>>(getModel(), "action"), false){

            @Override
            protected IModel<String> createTextModel(final IModel<SynchronizationActionType> model) {
                return new Model<String>() {

                    @Override
                    public String getObject() {
                        SynchronizationActionType action = model.getObject();

                        if(action == null){
                            return null;
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append(action.getName() != null ? action.getName() : " - ");

                        if(action.getHandlerUri() != null){
                            String[] handlerUriSplit = action.getHandlerUri().split("#");
                            sb.append("(");
                            sb.append(handlerUriSplit[handlerUriSplit.length - 1]);
                            sb.append(")");
                        }

                        return sb.toString();
                    }
                };
            }

            @Override
            protected SynchronizationActionType createNewEmptyItem(){
                return new SynchronizationActionType();
            }

            @Override
            protected void editPerformed(AjaxRequestTarget target, SynchronizationActionType object){
                actionEditPerformed(target, object);
            }
        };
        action.setOutputMarkupId(true);
        add(action);

        Label situationTooltip = new Label(ID_T_SITUATION);
        situationTooltip.add(new InfoTooltipBehavior());
        add(situationTooltip);

        Label channelTooltip = new Label(ID_T_CHANNEL);
        channelTooltip.add(new InfoTooltipBehavior());
        add(channelTooltip);

        Label synchronizeTooltip = new Label(ID_T_SYNCHRONIZE);
        synchronizeTooltip.add(new InfoTooltipBehavior());
        add(synchronizeTooltip);

        Label reconcileTooltip = new Label(ID_T_RECONCILE);
        reconcileTooltip.add(new InfoTooltipBehavior());
        add(reconcileTooltip);

        Label objTemplateTooltip = new Label(ID_T_OBJ_TEMPLATE);
        objTemplateTooltip.add(new InfoTooltipBehavior());
        add(objTemplateTooltip);

        Label actionTooltip = new Label(ID_T_ACTION);
        actionTooltip.add(new InfoTooltipBehavior());
        add(actionTooltip);

        initModals();
    }

    private void initModals(){
        ModalWindow actionEditor = new SynchronizationActionEditorDialog(ID_ACTION_MODAL, null){

            @Override
            public void updateComponents(AjaxRequestTarget target){
                target.add(SynchronizationReactionEditor.this.get(ID_ACTION));
            }
        };
        add(actionEditor);
    }

    private List<ObjectReferenceType> createObjectTemplateList(){
        objectTemplateMap.clear();
        OperationResult result = new OperationResult(OPERATION_LOAD_OBJECT_TEMPLATES);
        Task task = getPageBase().createSimpleTask(OPERATION_LOAD_OBJECT_TEMPLATES);
        List<PrismObject<ObjectTemplateType>> templates = null;
        List<ObjectReferenceType> references = new ArrayList<>();

        try{
            templates = getPageBase().getModelService().searchObjects(ObjectTemplateType.class, new ObjectQuery(), null, task, result);
            result.recomputeStatus();
        } catch (Exception e){
            result.recordFatalError("Couldn't load object templates from repository. ", e);
            LoggingUtils.logException(LOGGER, "Couldn't load object templates from repository", e);
        }

        // TODO - show error somehow
        // if(!result.isSuccess()){
        //    getPageBase().showResult(result);
        // }

        if(templates != null){
            ObjectReferenceType ref;

            for(PrismObject<ObjectTemplateType> template: templates){
                objectTemplateMap.put(template.getOid(), WebComponentUtil.getName(template));
                ref = new ObjectReferenceType();
                ref.setType(ObjectTemplateType.COMPLEX_TYPE);
                ref.setOid(template.getOid());
                references.add(ref);
            }
        }

        return references;
    }

    private void actionEditPerformed(AjaxRequestTarget target, SynchronizationActionType action){
        SynchronizationActionEditorDialog window = (SynchronizationActionEditorDialog) get(ID_ACTION_MODAL);
        window.updateModel(target, action);
        window.show(target);
    }
}
