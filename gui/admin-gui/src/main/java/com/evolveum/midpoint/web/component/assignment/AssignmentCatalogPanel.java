/*
 * Copyright (c) 2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.component.assignment;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.ObjectBrowserPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.RoleSelectionSpecification;
import com.evolveum.midpoint.model.api.context.*;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.*;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.data.ObjectDataProvider;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.search.Search;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchPanel;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.orgs.OrgTreePanel;
import com.evolveum.midpoint.web.page.admin.users.dto.TreeStateSet;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.page.self.PageAssignmentsList;
import com.evolveum.midpoint.web.page.self.dto.AssignmentConflictDto;
import com.evolveum.midpoint.web.page.self.dto.AssignmentViewType;
import com.evolveum.midpoint.web.security.SecurityUtils;
import com.evolveum.midpoint.web.session.OrgTreeStateStorage;
import com.evolveum.midpoint.web.session.SessionStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.model.util.ListModel;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * Created by honchar.
 */
public class AssignmentCatalogPanel<F extends AbstractRoleType> extends BasePanel {
	private static final long serialVersionUID = 1L;

	private static String ID_TREE_PANEL_CONTAINER = "treePanelContainer";
    private static String ID_TREE_PANEL = "treePanel";
    private static String ID_CATALOG_ITEMS_PANEL_CONTAINER = "catalogItemsPanelContainer";
    private static String ID_ASSIGNMENTS_OWNER_NAME = "assignmentsOwnerName";
    private static String ID_CATALOG_ITEMS_PANEL = "catalogItemsPanel";
    private static final String ID_CART_BUTTON = "cartButton";
    private static final String ID_CART_ITEMS_COUNT = "itemsCount";
    private static final String ID_HEADER_PANEL = "headerPanel";
    private static final String ID_VIEW_TYPE = "viewTypeSelect";
    private static final String ID_SEARCH_FORM = "searchForm";
    private static final String ID_SEARCH = "search";
    private static final String ID_PANEL_CONTAINER = "catalogItemsPanelContainer";
    private static final String ID_TARGET_USER_CONTAINER = "targetUserContainer";
    private static final String ID_TARGET_USER_BUTTON = "targetUserButton";
    private static final String ID_DELETE_TARGET_USER_BUTTON = "deleteTargetUserButton";
    private static final String ID_TARGET_USER_LABEL = "targetUserLabel";

    private static final String DOT_CLASS = AssignmentCatalogPanel.class.getName();
    private static final Trace LOGGER = TraceManager.getTrace(AssignmentCatalogPanel.class);
    private static final String OPERATION_LOAD_ASSIGNABLE_ROLES = DOT_CLASS + "loadAssignableRoles";

    private PageBase pageBase;
    private IModel<String> selectedTreeItemOidModel;
    private String rootOid;
    private IModel<Search> searchModel;
    private IModel<AssignmentViewType> viewModel;
    private IModel<PrismObject<UserType>> targetUserModel;
    private ObjectDataProvider<AssignmentEditorDto, AbstractRoleType> objectProvider;
    private ListDataProvider<AssignmentEditorDto> listProvider;
    private int itemsPerRow = 4;
    private boolean showUserSelectionPopup = true;
    private List<AssignmentEditorDto> listProviderData;


    public AssignmentCatalogPanel(String id) {
        super(id);
    }

    public AssignmentCatalogPanel(String id, String rootOid, PageBase pageBase) {
        super(id);
        this.pageBase = pageBase;
        this.rootOid = rootOid;
        AssignmentViewType.saveViewTypeToSession(pageBase, AssignmentViewType.ROLE_CATALOG_VIEW);
        initLayout();
    }

    public AssignmentCatalogPanel(String id, PageBase pageBase) {
        this(id, AssignmentViewType.getViewTypeFromSession(pageBase), pageBase);
    }

     public AssignmentCatalogPanel(String id, AssignmentViewType viewType, PageBase pageBase) {
        super(id);
        this.pageBase = pageBase;
        AssignmentViewType.saveViewTypeToSession(pageBase, viewType);
         initLayout();
    }

    protected void initProvider() {
        if (isCatalogOidEmpty()){
            objectProvider = null;
        } else {
            objectProvider = new ObjectDataProvider<AssignmentEditorDto, AbstractRoleType>(pageBase, AbstractRoleType.class) {
                private static final long serialVersionUID = 1L;

                @Override
                public AssignmentEditorDto createDataObjectWrapper(PrismObject<AbstractRoleType> obj) {
                    return AssignmentEditorDto.createDtoFromObject(obj.asObjectable(), UserDtoStatus.ADD, pageBase);
                }

                @Override
                public void setQuery(ObjectQuery query) {

                    super.setQuery(query);
                }

                @Override
                public ObjectQuery getQuery() {

                    return createContentQuery(null);
                }
            };
        }
    }

    private void initLayout() {
        initModels();
        initProvider();
        setOutputMarkupId(true);
        initHeaderPanel();

        WebMarkupContainer panelContainer = new WebMarkupContainer(ID_CATALOG_ITEMS_PANEL_CONTAINER);
        panelContainer.setOutputMarkupId(true);
        add(panelContainer);

        addOrReplaceLayout(null, panelContainer);
    }

    private void initHeaderPanel(){
        WebMarkupContainer headerPanel = new WebMarkupContainer(ID_HEADER_PANEL);
        headerPanel.setOutputMarkupId(true);
        add(headerPanel);

        initViewSelector(headerPanel);
        initUserSelectionPanel(headerPanel);
        initCartButton(headerPanel);
        initSearchPanel(headerPanel);
    }
    public void addOrReplaceLayout(AjaxRequestTarget target, WebMarkupContainer panelContainer) {
        WebMarkupContainer treePanelContainer = new WebMarkupContainer(ID_TREE_PANEL_CONTAINER);
        treePanelContainer.setOutputMarkupId(true);
        panelContainer.addOrReplace(treePanelContainer);
        if (AssignmentViewType.ROLE_CATALOG_VIEW.equals(AssignmentViewType.getViewTypeFromSession(pageBase)) && StringUtils.isNotEmpty(rootOid)) {
            // not let tree panel initializing in case of empty role catalog oid
            OrgTreePanel treePanel = new OrgTreePanel(ID_TREE_PANEL, Model.of(rootOid), false, "AssignmentShoppingCartPanel.treeTitle") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void selectTreeItemPerformed(SelectableBean<OrgType> selected,
                                                       AjaxRequestTarget target) {
                    AssignmentCatalogPanel.this.selectTreeItemPerformed(selected, target);
                }

                protected List<InlineMenuItem> createTreeMenu() {
                    return new ArrayList<>();
                }

                @Override
                protected List<InlineMenuItem> createTreeChildrenMenu() {
                    return new ArrayList<>();
                }

                @Override
                protected OrgTreeStateStorage getOrgTreeStateStorage(){
                    return pageBase.getSessionStorage().getRoleCatalog();
                }
            };
            treePanel.setOutputMarkupId(true);
            treePanelContainer.add(new AttributeAppender("class", "col-md-3"));
            treePanelContainer.add(new VisibleEnableBehaviour(){
                @Override
                public boolean isVisible(){
                    return !isCatalogOidEmpty();
                }
            });
            treePanelContainer.addOrReplace(treePanel);
        } else {
            WebMarkupContainer treePanel = new WebMarkupContainer(ID_TREE_PANEL);
            treePanel.setVisible(false);
            treePanel.setOutputMarkupId(true);
            treePanelContainer.addOrReplace(treePanel);
        }

        WebMarkupContainer catalogItemsPanelContainer = new WebMarkupContainer(ID_CATALOG_ITEMS_PANEL_CONTAINER);
        catalogItemsPanelContainer.setOutputMarkupId(true);
        panelContainer.addOrReplace(catalogItemsPanelContainer);

        String assignmentsOwnerName = pageBase.getSessionStorage().getRoleCatalog().getAssignmentsUserOwner() != null ?
                pageBase.getSessionStorage().getRoleCatalog().getAssignmentsUserOwner().getName().getOrig() : "";
        Label assignmentsOwnerLabel = new Label(ID_ASSIGNMENTS_OWNER_NAME,
                createStringResource("AssignmentCatalogPanel.assignmentsOwner", assignmentsOwnerName));
        assignmentsOwnerLabel.add(new VisibleEnableBehaviour(){
            @Override
            public boolean isVisible(){
                return AssignmentViewType.USER_TYPE.equals(pageBase.getSessionStorage().getRoleCatalog().getViewType());
            }
        });
        catalogItemsPanelContainer.add(assignmentsOwnerLabel);

        CatalogItemsPanel catalogItemsPanel;
        if (AssignmentViewType.USER_TYPE.equals(viewModel.getObject())) {
            PrismObject<UserType> assignmentsOwner = pageBase.getSessionStorage().getRoleCatalog().getAssignmentsUserOwner();
            listProviderData = new ArrayList<>();
            if (assignmentsOwner != null) {
                List<AssignmentType> assignments = assignmentsOwner.asObjectable().getAssignment();
                for (AssignmentType assignment : assignments) {
                    if (assignment.getTargetRef() == null ||
                            !UserType.COMPLEX_TYPE.equals(assignment.getTargetRef().getType())) {
                        assignment.setId(null);
                        listProviderData.add(new AssignmentEditorDto(UserDtoStatus.MODIFY, assignment, pageBase));
                    }
                }
                Collections.sort(listProviderData);
            }
            ListDataProvider listDataProvider = new ListDataProvider(this, Model.ofList(listProviderData));
            catalogItemsPanel = new CatalogItemsPanel(ID_CATALOG_ITEMS_PANEL,
                    pageBase, itemsPerRow, listDataProvider);
        } else {
            catalogItemsPanel = new CatalogItemsPanel(ID_CATALOG_ITEMS_PANEL, selectedTreeItemOidModel,
                    pageBase, itemsPerRow, objectProvider);
        }
        if (AssignmentViewType.ROLE_CATALOG_VIEW.equals(AssignmentViewType.getViewTypeFromSession(pageBase))) {
            catalogItemsPanelContainer.add(new AttributeAppender("class", "col-md-9"));
        } else {
            catalogItemsPanelContainer.add(new AttributeAppender("class", "col-md-12"));
        }
        catalogItemsPanel.setOutputMarkupId(true);
        catalogItemsPanelContainer.addOrReplace(catalogItemsPanel);
    }

    private void selectTreeItemPerformed(SelectableBean<OrgType> selected, AjaxRequestTarget target) {
        final OrgType selectedOrg = selected.getValue();
        if (selectedOrg == null) {
            return;
        }
        selectedTreeItemOidModel.setObject(selectedOrg.getOid());
        AssignmentViewType.saveViewTypeToSession(pageBase, AssignmentViewType.ROLE_CATALOG_VIEW);
        AssignmentCatalogPanel.this.addOrReplaceLayout(null, getCatalogItemsPanelContainer());
        target.add(getCatalogItemsPanelContainer());

    }

    private void initModels(){
        selectedTreeItemOidModel = new IModel<String>() {
            @Override
            public String getObject() {
                return StringUtils.isEmpty(pageBase.getSessionStorage().getRoleCatalog().getSelectedOid()) ?
                        rootOid : pageBase.getSessionStorage().getRoleCatalog().getSelectedOid();
            }

            @Override
            public void setObject(String s) {
                pageBase.getSessionStorage().getRoleCatalog().setSelectedOid(s);
            }

            @Override
            public void detach() {

            }
        };
        viewModel = new IModel<AssignmentViewType>() {
            @Override
            public AssignmentViewType getObject() {
                return AssignmentViewType.getViewTypeFromSession(pageBase);
            }

            @Override
            public void setObject(AssignmentViewType assignmentViewType) {
                AssignmentViewType.saveViewTypeToSession(pageBase, assignmentViewType);
            }

            @Override
            public void detach() {

            }
        };

        searchModel = new LoadableModel<Search>(false) {
            @Override
            public Search load() {
                Search search = SearchFactory.createSearch(AbstractRoleType.class, pageBase.getPrismContext(),
                        pageBase.getModelInteractionService());
                return search;
            }
        };

        targetUserModel = new IModel<PrismObject<UserType>>() {
            @Override
            public PrismObject<UserType> getObject() {
                PrismObject<UserType> targetUser = pageBase.getSessionStorage().getRoleCatalog().getTargetUser();
                if (targetUser != null){
                    return targetUser;
                }
                PrismObject<UserType> user = pageBase.loadUserSelf(pageBase);
                if (user != null){
                    return user;
                }
                return null;
            }

            @Override
            public void setObject(PrismObject<UserType> userType) {
                pageBase.getSessionStorage().getRoleCatalog().setTargetUser(userType);
            }

            @Override
            public void detach() {

            }
        };
    }

    public String getRootOid() {
        return rootOid;
    }

    public void setRootOid(String rootOid) {
        this.rootOid = rootOid;
    }

    private void initUserSelectionPanel(WebMarkupContainer headerPanel){
        WebMarkupContainer targetUserContainer = new WebMarkupContainer(ID_TARGET_USER_CONTAINER);
        targetUserContainer.setOutputMarkupId(true);
        AjaxLink<String> targetUserButton = new AjaxLink<String>(ID_TARGET_USER_BUTTON) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                if (showUserSelectionPopup) {
                    initUserSelectionPopup(createStringResource("AssignmentCatalogPanel.selectTargetUser"), true, target);
                }
            }
        };
        targetUserContainer.add(targetUserButton);

        Label label = new Label(ID_TARGET_USER_LABEL, getTargetUserLabelModel());
        label.setRenderBodyOnly(true);
        targetUserButton.add(label);

        AjaxLink deleteButton = new AjaxLink(ID_DELETE_TARGET_USER_BUTTON) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                showUserSelectionPopup = false;
                pageBase.getSessionStorage().getRoleCatalog().setTargetUser(null);
                target.add(getTargetUserContainer());
            }
        };
        deleteButton.add(new VisibleEnableBehaviour(){
            @Override
            public boolean isVisible(){
                return pageBase.getSessionStorage().getRoleCatalog().getTargetUser() != null;
            }
        });
        targetUserButton.add(deleteButton);
        headerPanel.add(targetUserContainer);
    }
    private void initViewSelector(WebMarkupContainer headerPanel){
        DropDownChoice<AssignmentViewType> viewSelect = new DropDownChoice(ID_VIEW_TYPE, viewModel, new ListModel(createAssignableTypesList()),
                new EnumChoiceRenderer<AssignmentViewType>(this));
        viewSelect.add(new OnChangeAjaxBehavior() {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                if (AssignmentViewType.USER_TYPE.equals(viewSelect.getModelObject())){
                    initUserSelectionPopup(createStringResource("AssignmentCatalogPanel.selectAssignmentsUserOwner"),
                            false, target);
                } else {
                    AssignmentCatalogPanel.this.addOrReplaceLayout(target, getCatalogItemsPanelContainer());
                    target.add(getCatalogItemsPanelContainer());
                    target.add(getHeaderPanel());
                }
            }
        });
        viewSelect.setOutputMarkupId(true);
        headerPanel.add(viewSelect);

    }

    private WebMarkupContainer getCatalogItemsPanelContainer(){
        return (WebMarkupContainer)get(ID_CATALOG_ITEMS_PANEL_CONTAINER);
    }

    private WebMarkupContainer getHeaderPanel(){
        return (WebMarkupContainer)get(ID_HEADER_PANEL);
    }

    private DropDownChoice getViewSelectComponent(){
        return (DropDownChoice)getHeaderPanel().get(ID_VIEW_TYPE);
    }
    private void initSearchPanel(WebMarkupContainer headerPanel) {
        final Form searchForm = new Form(ID_SEARCH_FORM);
        headerPanel.add(searchForm);
        searchForm.add(new VisibleEnableBehaviour() {
            public boolean isVisible() {
                return !isCatalogOidEmpty()
                        && !AssignmentViewType.USER_TYPE.equals(pageBase.getSessionStorage().getRoleCatalog().getViewType());
            }
        });
        searchForm.setOutputMarkupId(true);

        SearchPanel search = new SearchPanel(ID_SEARCH, (IModel) searchModel, false) {

            @Override
            public void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
                AssignmentCatalogPanel.this.searchPerformed(query, target);
            }
        };
        searchForm.add(search);

    }

    private void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
//        setCurrentPage(0);
        objectProvider.setQuery(createContentQuery(query));
        AssignmentCatalogPanel.this.addOrReplaceLayout(null, getCatalogItemsPanelContainer());
        target.add(getCatalogItemsPanelContainer());
    }

    protected ObjectQuery createContentQuery(ObjectQuery searchQuery) {
        ObjectQuery memberQuery = null;
        if (AssignmentViewType.ROLE_CATALOG_VIEW.equals(AssignmentViewType.getViewTypeFromSession(pageBase))){
            String oid = selectedTreeItemOidModel.getObject();
            if (StringUtils.isNotEmpty(oid)) {
                memberQuery = createMemberQuery(oid);
            }
        } else {
            memberQuery = createMemberQuery(getViewTypeClass(AssignmentViewType.getViewTypeFromSession(pageBase)));
        }
        if (memberQuery == null) {
            memberQuery = new ObjectQuery();
        }
        if (searchQuery == null) {
            if (searchModel != null && searchModel.getObject() != null) {
                Search search = searchModel.getObject();
                searchQuery = search.createObjectQuery(pageBase.getPrismContext());
            }
        }
        if (searchQuery != null && searchQuery.getFilter() != null) {
            memberQuery.addFilter(searchQuery.getFilter());
        }
        return memberQuery;
    }

    private ObjectQuery createMemberQuery(QName focusTypeClass) {
        ObjectQuery query = new ObjectQuery();
        ObjectFilter filter = null;
        if (focusTypeClass.equals(RoleType.COMPLEX_TYPE)) {
            LOGGER.debug("Loading roles which the current user has right to assign");
            OperationResult result = new OperationResult(OPERATION_LOAD_ASSIGNABLE_ROLES);
            try {
                ModelInteractionService mis = pageBase.getModelInteractionService();
                RoleSelectionSpecification roleSpec =
                        mis.getAssignableRoleSpecification(SecurityUtils.getPrincipalUser().getUser().asPrismObject(), result);
                filter = roleSpec.getFilter();
            } catch (Exception ex) {
                LoggingUtils.logUnexpectedException(LOGGER, "Couldn't load available roles", ex);
                result.recordFatalError("Couldn't load available roles", ex);
            } finally {
                result.recomputeStatus();
            }
            if (!result.isSuccess() && !result.isHandledError()) {
                pageBase.showResult(result);
            }
        }
        query.addFilter(TypeFilter.createType(focusTypeClass, filter));
        return query;

    }

    private ObjectQuery createMemberQuery(String oid) {
        ObjectFilter filter = OrgFilter.createOrg(oid, OrgFilter.Scope.ONE_LEVEL);

        TypeFilter roleTypeFilter = TypeFilter.createType(RoleType.COMPLEX_TYPE, filter);
        TypeFilter orgTypeFilter = TypeFilter.createType(OrgType.COMPLEX_TYPE, filter);
        TypeFilter serviceTypeFilter = TypeFilter.createType(ServiceType.COMPLEX_TYPE, filter);
        ObjectQuery query = ObjectQuery.createObjectQuery(OrFilter.createOr(roleTypeFilter, orgTypeFilter, serviceTypeFilter));
        return query;

    }

    private void initCartButton(WebMarkupContainer headerPanel){
        AjaxButton cartButton = new AjaxButton(ID_CART_BUTTON) {
            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                PageAssignmentsList assignmentsPanel = new PageAssignmentsList(true);
                pageBase.navigateToNext(assignmentsPanel);
            }
        };
        cartButton.add(new VisibleEnableBehaviour(){
            public boolean isVisible(){
                return !isCatalogOidEmpty();
            }
        });
        cartButton.setOutputMarkupId(true);
        headerPanel.add(cartButton);

        Label cartItemsCount = new Label(ID_CART_ITEMS_COUNT, new IModel<String>() {
            @Override
            public String getObject() {
                SessionStorage storage = pageBase.getSessionStorage();
                return Integer.toString(storage.getRoleCatalog().getAssignmentShoppingCart().size());
            }

            @Override
            public void setObject(String s) {


            }

            @Override
            public void detach() {

            }
        });
        cartItemsCount.add(new VisibleEnableBehaviour() {
            @Override
            public boolean isVisible() {
                SessionStorage storage = pageBase.getSessionStorage();
                if (storage.getRoleCatalog().getAssignmentShoppingCart().size() == 0) {
                    return false;
                } else {
                    return true;
                }
            }
        });
        cartItemsCount.setOutputMarkupId(true);
        cartButton.add(cartItemsCount);
    }

    public void reloadCartButton(AjaxRequestTarget target) {
        target.add(get(ID_HEADER_PANEL).get(ID_CART_BUTTON));
    }

    private QName getViewTypeClass(AssignmentViewType viewType) {
        if (AssignmentViewType.ORG_TYPE.equals(viewType)) {
            return OrgType.COMPLEX_TYPE;
        } else if (AssignmentViewType.ROLE_TYPE.equals(viewType)) {
            return RoleType.COMPLEX_TYPE;
        } else if (AssignmentViewType.SERVICE_TYPE.equals(viewType)) {
            return ServiceType.COMPLEX_TYPE;
        }
        return null;
    }

    public static List<AssignmentViewType> createAssignableTypesList() {
        List<AssignmentViewType> focusTypeList = new ArrayList<>();

        focusTypeList.add(AssignmentViewType.ROLE_CATALOG_VIEW);
        focusTypeList.add(AssignmentViewType.ORG_TYPE);
        focusTypeList.add(AssignmentViewType.ROLE_TYPE);
        focusTypeList.add(AssignmentViewType.SERVICE_TYPE);
        focusTypeList.add(AssignmentViewType.USER_TYPE);

        return focusTypeList;
    }

    private boolean isCatalogOidEmpty(){
        String oid = selectedTreeItemOidModel != null ? selectedTreeItemOidModel.getObject() : "";
        return AssignmentViewType.ROLE_CATALOG_VIEW.equals(AssignmentViewType.getViewTypeFromSession(pageBase)) &&
                (selectedTreeItemOidModel == null || StringUtils.isEmpty(oid));
    }

    private void initUserSelectionPopup(StringResourceModel title, boolean targetUserSelection, AjaxRequestTarget target) {

        List<QName> supportedTypes = new ArrayList<>();
        supportedTypes.add(getPageBase().getPrismContext().getSchemaRegistry()
                .findObjectDefinitionByCompileTimeClass(UserType.class).getTypeName());
        ObjectBrowserPanel<UserType> focusBrowser = new ObjectBrowserPanel<UserType>(getPageBase().getMainPopupBodyId(),
                UserType.class, supportedTypes, false, getPageBase()) {
            @Override
            protected void onSelectPerformed(AjaxRequestTarget target, UserType targetUser) {
                super.onSelectPerformed(target, targetUser);
                if (targetUserSelection) {
                    pageBase.getSessionStorage().getRoleCatalog().setTargetUser(targetUser.asPrismContainer());
                    target.add(getTargetUserContainer());
                } else {
                    pageBase.getSessionStorage().getRoleCatalog().setAssignmentsUserOwner(targetUser.asPrismContainer());
                    AssignmentCatalogPanel.this.addOrReplaceLayout(target, getCatalogItemsPanelContainer());
                    target.add(getCatalogItemsPanelContainer());
                    target.add(getHeaderPanel());
                    target.add(getViewSelectComponent());
                }
            }

            @Override
            public StringResourceModel getTitle() {
                return title;
            }

        };
        getPageBase().showMainPopup(focusBrowser, target);
    }

    private WebMarkupContainer getTargetUserContainer(){
        return (WebMarkupContainer)get(ID_HEADER_PANEL).get(ID_TARGET_USER_CONTAINER);
    }
    private IModel<String> getTargetUserLabelModel(){
        return new IModel<String>() {
            @Override
            public String getObject() {
                if (pageBase.getSessionStorage().getRoleCatalog().getTargetUser() == null){
                    return createStringResource("AssignmentCatalogPanel.requestForMe").getString();
                }
                if (targetUserModel != null && targetUserModel.getObject() != null){
                    return createStringResource("AssignmentCatalogPanel.requestFor").getString() +
                            " " + targetUserModel.getObject().getName().getOrig();
                }
                return "";
            }

            @Override
            public void setObject(String s) {
            }

            @Override
            public void detach() {

            }
        };
    }
}

