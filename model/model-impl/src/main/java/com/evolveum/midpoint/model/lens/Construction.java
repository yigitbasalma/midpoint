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
package com.evolveum.midpoint.model.lens;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.model.common.expression.ObjectDeltaObject;
import com.evolveum.midpoint.model.common.mapping.Mapping;
import com.evolveum.midpoint.model.common.mapping.MappingFactory;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceAttributeDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowType;

/**
 * Live class that contains "construction" - a definition how to construct a resource object. It in fact reflects
 * the definition of ConstructionType but it also contains "live" objects and can evaluate the construction. It also
 * contains intermediary and side results of the evaluation.
 * 
 * @author Radovan Semancik
 *
 * This class is Serializable but it is not in fact serializable. It implements Serializable interface only
 * to be storable in the PrismPropertyValue.
 */
public class Construction<F extends FocusType> implements DebugDumpable, Dumpable, Serializable {

	private AssignmentPath assignmentPath;
	private ConstructionType constructionType;
	private ObjectType source;
	private OriginType originType;
	private String channel;
	private LensContext<F> lensContext;
	private ObjectDeltaObject<F> userOdo;
	private ResourceType resource;
	private ObjectResolver objectResolver;
	private MappingFactory mappingFactory;
	private Collection<Mapping<? extends PrismPropertyValue<?>>> attributeMappings;
	private Collection<Mapping<PrismContainerValue<ShadowAssociationType>>> associationMappings;
	private RefinedObjectClassDefinition refinedAccountDefinition;
	private PrismContainerValue<AssignmentType> magicAssignment;
	private PrismContainerValue<AssignmentType> immediateAssignment;
	private PrismContainerValue<AssignmentType> thisAssignment;
	private PrismObject<? extends AbstractRoleType> immediateRole;
	private PrismContext prismContext;
	private PrismContainerDefinition<ShadowAssociationType> associationContainerDefinition;
	
	private static final Trace LOGGER = TraceManager.getTrace(Construction.class);
	
	public Construction(ConstructionType constructionType, ObjectType source) {
		this.constructionType = constructionType;
		this.source = source;
		this.assignmentPath = null;
		this.attributeMappings = null;
	}

	public void setSource(ObjectType source) {
		this.source = source;
	}
		
	public ObjectType getSource() {
		return source;
	}

	public OriginType getOriginType() {
		return originType;
	}

	public void setOriginType(OriginType originType) {
		this.originType = originType;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}
	
	public LensContext<F> getLensContext() {
		return lensContext;
	}

	public void setLensContext(LensContext<F> lensContext) {
		this.lensContext = lensContext;
	}

	public void setUserOdo(ObjectDeltaObject<F> userOdo) {
		this.userOdo = userOdo;
	}

	public ObjectResolver getObjectResolver() {
		return objectResolver;
	}

	public void setObjectResolver(ObjectResolver objectResolver) {
		this.objectResolver = objectResolver;
	}

	PrismContext getPrismContext() {
		return prismContext;
	}

	void setPrismContext(PrismContext prismContext) {
		this.prismContext = prismContext;
	}

	public MappingFactory getMappingFactory() {
		return mappingFactory;
	}

	public void setMappingFactory(MappingFactory mappingFactory) {
		this.mappingFactory = mappingFactory;
	}

	public ShadowKindType getKind() {
		if (refinedAccountDefinition == null) {
			throw new IllegalStateException("Kind can only be fetched from evaluated Construction");
		}
		return refinedAccountDefinition.getKind();
	}

	public String getIntent() {
		if (refinedAccountDefinition == null) {
			throw new IllegalStateException("Intent can only be fetched from evaluated Construction");
		}
		return refinedAccountDefinition.getIntent();
	}

	public Object getDescription() {
		return constructionType.getDescription();
	}
	
	public Collection<Mapping<? extends PrismPropertyValue<?>>> getAttributeMappings() {
		if (attributeMappings == null) {
			attributeMappings = new ArrayList<Mapping<? extends PrismPropertyValue<?>>>();
		}
		return attributeMappings;
	}
	
	public Mapping<? extends PrismPropertyValue<?>> getAttributeMapping(QName attrName) {
		for (Mapping<? extends PrismPropertyValue<?>> myVc : getAttributeMappings()) {
			if (myVc.getItemName().equals(attrName)) {
				return myVc;
			}
		}
		return null;
	}
	
	public void addAttributeMapping(Mapping<? extends PrismPropertyValue<?>> valueConstruction) {
		getAttributeMappings().add(valueConstruction);
	}

	public boolean containsAttributeMapping(QName attributeName) {
		for (Mapping<?> myVc: getAttributeMappings()) {
			if (attributeName.equals(myVc.getItemName())) {
				return true;
			}
		}
		return false;
	}
	
	public Collection<Mapping<PrismContainerValue<ShadowAssociationType>>> getAssociationMappings() {
		if (associationMappings == null) {
			associationMappings = new ArrayList<Mapping<PrismContainerValue<ShadowAssociationType>>>();
		}
		return associationMappings;
	}

	public AssignmentPath getAssignmentPath() {
		return assignmentPath;
	}

	public void setAssignmentPath(AssignmentPath assignmentPath) {
		this.assignmentPath = assignmentPath;
	}

	public ResourceType getResource(OperationResult result) throws ObjectNotFoundException, SchemaException {
		if (resource == null) {
			if (constructionType.getResource() != null) {
				resource = constructionType.getResource();
			} else if (constructionType.getResourceRef() != null) {
				try {
					resource = objectResolver.resolve(constructionType.getResourceRef(), ResourceType.class,
							"account construction in "+ source , result);
				} catch (ObjectNotFoundException e) {
					throw new ObjectNotFoundException("Resource reference seems to be invalid in account construction in " + source + ": "+e.getMessage(), e);
				}
			}
			if (resource == null) {
				throw new SchemaException("No resource set in account construction in " + source);
			}
		}
		return resource;
	}
	
	public void evaluate(Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException {
		evaluateKindIntent(result);
		computeVariables(result);
		evaluateAttributes(task, result);
		evaluateAssociations(task, result);
	}
	
	private void evaluateKindIntent(OperationResult result) throws SchemaException, ObjectNotFoundException {
		String resourceOid = null;
		if (constructionType.getResourceRef() != null) {
			resourceOid = constructionType.getResourceRef().getOid();
		}
		if (constructionType.getResource() != null) {
			resourceOid = constructionType.getResource().getOid();
		}
		ResourceType resource = getResource(result);
		if (!resource.getOid().equals(resourceOid)) {
			throw new IllegalStateException("The specified resource and the resource in construction does not match");
		}
		
		RefinedResourceSchema refinedSchema = RefinedResourceSchema.getRefinedSchema(resource, LayerType.MODEL, prismContext);
		if (refinedSchema == null) {
			// Refined schema may be null in some error-related border cases
			throw new SchemaException("No (refined) schema for "+resource);
		}
		
		ShadowKindType kind = constructionType.getKind();
		if (kind == null) {
			kind = ShadowKindType.ACCOUNT;
		}
		refinedAccountDefinition = refinedSchema.getRefinedDefinition(kind, constructionType.getIntent());
		
		if (refinedAccountDefinition == null) {
			if (constructionType.getIntent() != null) {
				throw new SchemaException("No account type '"+constructionType.getIntent()+"' found in "+ObjectTypeUtil.toShortString(getResource(result))+" as specified in account construction in "+ObjectTypeUtil.toShortString(source));
			} else {
				throw new SchemaException("No default account type found in " + resource + " as specified in account construction in "+ObjectTypeUtil.toShortString(source));
			}
		}
	}
	
	private void computeVariables(OperationResult result) throws SchemaException {
		Iterator<AssignmentPathSegment> iterator = assignmentPath.getSegments().iterator();
		while (iterator.hasNext()) {
			AssignmentPathSegment segment = iterator.next();
			AssignmentType segmentAssignmentType = segment.getAssignmentType();
			PrismContainerValue<AssignmentType> segmentAssignmentCVal = segmentAssignmentType.asPrismContainerValue();
			PrismContainerDefinition<AssignmentType> assignmentDef = segmentAssignmentCVal.getParent().getDefinition();
			
			// Magic assignment
			if (magicAssignment == null) {
				magicAssignment = segmentAssignmentCVal.clone();
				// Make sure that the magic assignment has a valid parent so it can be serialized
				PrismContainer<AssignmentType> assignmentCont = assignmentDef.instantiate();
				assignmentCont.add(magicAssignment);
			} else {
				// Collect extension values from the assignment extension
				PrismContainer<Containerable> magicExtension = magicAssignment.findOrCreateContainer(AssignmentType.F_EXTENSION);
				mergeExtension(magicExtension, segmentAssignmentCVal.findContainer(AssignmentType.F_EXTENSION));
			}
			
			// Collect extension values from the source object extension
			PrismContainer<Containerable> magicExtension = magicAssignment.findOrCreateContainer(AssignmentType.F_EXTENSION);
			ObjectType segmentSource = segment.getSource();
			if (segmentSource != null) {
				mergeExtension(magicExtension, segmentSource.asPrismObject().findContainer(AssignmentType.F_EXTENSION));
			}
			
			// immediate assignment (use assignment from previous iteration)
			immediateAssignment = thisAssignment;
			
			// this assignment
			thisAssignment = segmentAssignmentCVal.clone();
			// Make sure that the assignment has a valid parent so it can be serialized
			PrismContainer<AssignmentType> assignmentCont = assignmentDef.instantiate();
			assignmentCont.add(thisAssignment);
			
			if (iterator.hasNext() && segmentSource instanceof AbstractRoleType) {
				immediateRole = segmentSource.asPrismObject();
			}
		}
	}

	private void mergeExtension(PrismContainer<Containerable> magicExtension, PrismContainer<Containerable> segmentExtension) throws SchemaException {
		if (segmentExtension != null) {
			for (Item<?> segmentItem: segmentExtension.getValue().getItems()) {
				Item<?> magicItem = magicExtension.findItem(segmentItem.getElementName());
				if (magicItem == null) {
					magicExtension.add(segmentItem.clone());
				}
			}
		}
	}

	private void evaluateAttributes(Task task, OperationResult result) throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		attributeMappings = new ArrayList<Mapping<? extends PrismPropertyValue<?>>>();
//		LOGGER.trace("Assignments used for account construction for {} ({}): {}", new Object[]{this.resource,
//				assignments.size(), assignments});
		for (ResourceAttributeDefinitionType attribudeDefinitionType : constructionType.getAttribute()) {
			QName attrName = attribudeDefinitionType.getRef();
			if (attrName == null) {
				throw new SchemaException("No attribute name (ref) in attribute definition in account construction in "+source);
			}
			if (!attribudeDefinitionType.getInbound().isEmpty()) {
				throw new SchemaException("Cannot process inbound section in definition of attribute "+attrName+" in account construction in "+source);
			}
			MappingType outboundMappingType = attribudeDefinitionType.getOutbound();
			if (outboundMappingType == null) {
				throw new SchemaException("No outbound section in definition of attribute "+attrName+" in account construction in "+source);
			}
			Mapping<? extends PrismPropertyValue<?>> attributeMapping = evaluateAttribute(attribudeDefinitionType, task, result);
			if (attributeMapping != null) {
				attributeMappings.add(attributeMapping);
			}
		}
	}

	private Mapping<? extends PrismPropertyValue<?>> evaluateAttribute(ResourceAttributeDefinitionType attribudeDefinitionType,
			Task task, OperationResult result) 
			throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		QName attrName = attribudeDefinitionType.getRef();
		if (attrName == null) {
			throw new SchemaException("Missing 'ref' in attribute construction in account construction in "+ObjectTypeUtil.toShortString(source));
		}
		if (!attribudeDefinitionType.getInbound().isEmpty()) {
			throw new SchemaException("Cannot process inbound section in definition of attribute "+attrName+" in account construction in "+source);
		}
		MappingType outboundMappingType = attribudeDefinitionType.getOutbound();
		if (outboundMappingType == null) {
			throw new SchemaException("No outbound section in definition of attribute "+attrName+" in account construction in "+source);
		}
		PrismPropertyDefinition outputDefinition = findAttributeDefinition(attrName);
		if (outputDefinition == null) {
			throw new SchemaException("Attribute "+attrName+" not found in schema for account type "+getIntent()+", "+ObjectTypeUtil.toShortString(getResource(result))+" as definied in "+ObjectTypeUtil.toShortString(source), attrName);
		}
		Mapping<? extends PrismPropertyValue<?>> mapping = mappingFactory.createMapping(outboundMappingType,
				"for attribute " + PrettyPrinter.prettyPrint(attrName)  + " in "+source);
		
		if (!mapping.isApplicableToChannel(channel)) {
			return null;
		}
		
		mapping.addVariableDefinition(ExpressionConstants.VAR_USER, userOdo);
		mapping.addVariableDefinition(ExpressionConstants.VAR_FOCUS, userOdo);
		mapping.addVariableDefinition(ExpressionConstants.VAR_SOURCE, source);
		mapping.setMappingQName(attrName);
		mapping.setSourceContext(userOdo);
		mapping.setRootNode(userOdo);
		mapping.setDefaultTargetDefinition(outputDefinition);
		mapping.setOriginType(originType);
		mapping.setOriginObject(source);
		if (!assignmentPath.isEmpty()) {
			AssignmentType assignmentType = assignmentPath.getFirstAssignment();
			mapping.addVariableDefinition(ExpressionConstants.VAR_ASSIGNMENT, magicAssignment);
			mapping.addVariableDefinition(ExpressionConstants.VAR_IMMEDIATE_ASSIGNMENT, immediateAssignment);
			mapping.addVariableDefinition(ExpressionConstants.VAR_THIS_ASSIGNMENT, thisAssignment);
			mapping.addVariableDefinition(ExpressionConstants.VAR_FOCUS_ASSIGNMENT, assignmentType.asPrismContainerValue());
			mapping.addVariableDefinition(ExpressionConstants.VAR_IMMEDIATE_ROLE, immediateRole);
		}
		// TODO: other variables ?
		
		// Set condition masks. There are used as a brakes to avoid evaluating to nonsense values in case user is not present
		// (e.g. in old values in ADD situations and new values in DELETE situations).
		if (userOdo.getOldObject() == null) {
			mapping.setConditionMaskOld(false);
		}
		if (userOdo.getNewObject() == null) {
			mapping.setConditionMaskNew(false);
		}

		LensUtil.evaluateMapping(mapping, lensContext, task, result);
		
		LOGGER.trace("Evaluated mapping for attribute "+attrName+": "+mapping);
		return mapping;
	}

	private ResourceAttributeDefinition findAttributeDefinition(QName attributeName) {
		return refinedAccountDefinition.findAttributeDefinition(attributeName);
	}
	
	public boolean hasValueForAttribute(QName attributeName) {
		for (Mapping<? extends PrismPropertyValue<?>> attributeConstruction: attributeMappings) {
			if (attributeName.equals(attributeConstruction.getItemName())) {
				PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> outputTriple = attributeConstruction.getOutputTriple();
				if (outputTriple != null && !outputTriple.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}
	
	private void evaluateAssociations(Task task, OperationResult result) throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		associationMappings = new ArrayList<Mapping<PrismContainerValue<ShadowAssociationType>>>();
		for (ResourceObjectAssociationType associationDefinitionType : constructionType.getAssociation()) {
			QName assocName = associationDefinitionType.getName();
			if (assocName == null) {
				throw new SchemaException("No association name (ref) in association definition in construction in "+source);
			}
			MappingType outboundMappingType = associationDefinitionType.getOutbound();
			if (outboundMappingType == null) {
				throw new SchemaException("No outbound section in definition of association "+assocName+" in construction in "+source);
			}
			Mapping<PrismContainerValue<ShadowAssociationType>> assocMapping = evaluateAssociation(associationDefinitionType, task, result);
			if (assocMapping != null) {
				associationMappings.add(assocMapping);
			}
		}
	}

	private Mapping<PrismContainerValue<ShadowAssociationType>> evaluateAssociation(ResourceObjectAssociationType associationDefinitionType,
			Task task, OperationResult result) 
			throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException {
		QName assocName = associationDefinitionType.getName();
		if (assocName == null) {
			throw new SchemaException("Missing 'ref' in association in construction in "+source);
		}
		MappingType outboundMappingType = associationDefinitionType.getOutbound();
		if (outboundMappingType == null) {
			throw new SchemaException("No outbound section in definition of association "+assocName+" in construction in "+source);
		}
		PrismContainerDefinition<ShadowAssociationType> outputDefinition = getAssociationContainerDefinition();
		Mapping<PrismContainerValue<ShadowAssociationType>> mapping = mappingFactory.createMapping(outboundMappingType,
				"for association " + PrettyPrinter.prettyPrint(assocName)  + " in " + source);
		
		if (!mapping.isApplicableToChannel(channel)) {
			return null;
		}
		
		mapping.addVariableDefinition(ExpressionConstants.VAR_USER, userOdo);
		mapping.addVariableDefinition(ExpressionConstants.VAR_FOCUS, userOdo);
		mapping.addVariableDefinition(ExpressionConstants.VAR_SOURCE, source);
		mapping.setMappingQName(assocName);
		mapping.setSourceContext(userOdo);
		mapping.setRootNode(userOdo);
		mapping.setDefaultTargetDefinition(outputDefinition);
		mapping.setOriginType(originType);
		mapping.setOriginObject(source);
		if (!assignmentPath.isEmpty()) {
			AssignmentType assignmentType = assignmentPath.getFirstAssignment();
			mapping.addVariableDefinition(ExpressionConstants.VAR_ASSIGNMENT, magicAssignment);
			mapping.addVariableDefinition(ExpressionConstants.VAR_IMMEDIATE_ASSIGNMENT, immediateAssignment);
			mapping.addVariableDefinition(ExpressionConstants.VAR_THIS_ASSIGNMENT, thisAssignment);
			mapping.addVariableDefinition(ExpressionConstants.VAR_FOCUS_ASSIGNMENT, assignmentType.asPrismContainerValue());
			mapping.addVariableDefinition(ExpressionConstants.VAR_IMMEDIATE_ROLE, immediateRole);
		}
		// TODO: other variables ?
		
		// Set condition masks. There are used as a brakes to avoid evaluating to nonsense values in case user is not present
		// (e.g. in old values in ADD situations and new values in DELETE situations).
		if (userOdo.getOldObject() == null) {
			mapping.setConditionMaskOld(false);
		}
		if (userOdo.getNewObject() == null) {
			mapping.setConditionMaskNew(false);
		}

		LensUtil.evaluateMapping(mapping, lensContext, task, result);
		
		LOGGER.trace("Evaluated mapping for association "+assocName+": "+mapping);
		return mapping;
	}
	
	private PrismContainerDefinition<ShadowAssociationType> getAssociationContainerDefinition() {
		if (associationContainerDefinition == null) {
			PrismObjectDefinition<ShadowType> shadowDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ShadowType.class);
			associationContainerDefinition = shadowDefinition.findContainerDefinition(ShadowType.F_ASSOCIATION);
		}
		return associationContainerDefinition;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((assignmentPath == null) ? 0 : assignmentPath.hashCode());
		result = prime * result + ((attributeMappings == null) ? 0 : attributeMappings.hashCode());
		result = prime * result + ((associationMappings == null) ? 0 : associationMappings.hashCode());
		result = prime * result
				+ ((refinedAccountDefinition == null) ? 0 : refinedAccountDefinition.hashCode());
		result = prime * result + ((resource == null) ? 0 : resource.hashCode());
		result = prime * result + ((source == null) ? 0 : source.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Construction other = (Construction) obj;
		if (assignmentPath == null) {
			if (other.assignmentPath != null)
				return false;
		} else if (!assignmentPath.equals(other.assignmentPath))
			return false;
		if (attributeMappings == null) {
			if (other.attributeMappings != null)
				return false;
		} else if (!attributeMappings.equals(other.attributeMappings))
			return false;
		if (associationMappings == null) {
			if (other.associationMappings != null)
				return false;
		} else if (!associationMappings.equals(other.associationMappings))
			return false;
		if (refinedAccountDefinition == null) {
			if (other.refinedAccountDefinition != null)
				return false;
		} else if (!refinedAccountDefinition.equals(other.refinedAccountDefinition))
			return false;
		if (resource == null) {
			if (other.resource != null)
				return false;
		} else if (!resource.equals(other.resource))
			return false;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.equals(other.source))
			return false;
		return true;
	}

	@Override
	public String dump() {
		return debugDump();
	}

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<indent;i++) {
			sb.append(INDENT_STRING);
		}
		sb.append("Construction(");
		if (refinedAccountDefinition == null) {
			sb.append("null");
		} else {
			sb.append(refinedAccountDefinition.getShadowDiscriminator());
		}
		sb.append(")");
		if (attributeMappings != null && !attributeMappings.isEmpty()) {
			sb.append("\n");
			DebugUtil.debugDumpLabel(sb, "attribute mappings", indent+1);
			for (Mapping mapping: attributeMappings) {
				sb.append("\n");
				sb.append(mapping.debugDump(indent+2));
			}
		}
		if (associationMappings != null && !associationMappings.isEmpty()) {
			sb.append("\n");
			DebugUtil.debugDumpLabel(sb, "association mappings", indent+1);
			for (Mapping mapping: associationMappings) {
				sb.append("\n");
				sb.append(mapping.debugDump(indent+2));
			}
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return "Construction(" + (refinedAccountDefinition == null ? "unknown" : refinedAccountDefinition.getShadowDiscriminator()) + ")";
	}
	
}
