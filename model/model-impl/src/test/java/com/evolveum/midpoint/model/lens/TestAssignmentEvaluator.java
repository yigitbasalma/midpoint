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
package com.evolveum.midpoint.model.lens;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static com.evolveum.midpoint.test.IntegrationTestTools.*;

import static com.evolveum.midpoint.model.lens.LensTestConstants.*;

import java.io.FileNotFoundException;

import javax.xml.bind.JAXBException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.common.expression.ObjectDeltaObject;
import com.evolveum.midpoint.model.AbstractInternalModelIntegrationTest;
import com.evolveum.midpoint.model.lens.Assignment;
import com.evolveum.midpoint.model.lens.AssignmentEvaluator;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

/**
 * @author semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestAssignmentEvaluator extends AbstractInternalModelIntegrationTest {

	@Autowired(required=true)
	private RepositoryService repositoryService;
	
	@Autowired(required=true)
	private ObjectResolver objectResolver;
	
	public TestAssignmentEvaluator() throws JAXBException {
		super();
	}

	@Test
	public void testDirect() throws ObjectNotFoundException, SchemaException, FileNotFoundException, JAXBException, ExpressionEvaluationException {
		displayTestTile(this, "testDirect");
		
		// GIVEN
		OperationResult result = new OperationResult(TestAssignmentEvaluator.class.getName() + ".testDirect");
		AssignmentEvaluator assignmentEvaluator = createAssignmentEvaluator();
		PrismAsserts.assertParentConsistency(userTypeJack.asPrismObject());
		
		AssignmentType assignmentType = unmarshallJaxbFromFile(ASSIGNMENT_DIRECT_FILENAME, AssignmentType.class);
		
		// We need to make sure that the assignment has a parent
		PrismContainerDefinition assignmentContainerDefinition = userTypeJack.asPrismObject().getDefinition().findContainerDefinition(UserType.F_ASSIGNMENT);
		PrismContainer assignmentContainer = assignmentContainerDefinition.instantiate();
		assignmentContainer.add(assignmentType.asPrismContainerValue());
		
		// WHEN
		Assignment evaluatedAssignment = assignmentEvaluator.evaluate(assignmentType, userTypeJack, "testDirect", result);
		
		// THEN
		assertNotNull(evaluatedAssignment);
		display("Evaluated assignment",evaluatedAssignment.dump());
		assertEquals(1,evaluatedAssignment.getAccountConstructions().size());
		PrismAsserts.assertParentConsistency(userTypeJack.asPrismObject());
	}
	
	private AssignmentEvaluator createAssignmentEvaluator() throws ObjectNotFoundException, SchemaException {
		AssignmentEvaluator assignmentEvaluator = new AssignmentEvaluator();
		assignmentEvaluator.setRepository(repositoryService);
		
		PrismObject<UserType> userJack = userTypeJack.asPrismObject();
		assignmentEvaluator.setUserOdo(new ObjectDeltaObject<UserType>(userJack, null, null));
		
		assignmentEvaluator.setObjectResolver(objectResolver);
		assignmentEvaluator.setPrismContext(prismContext);
		return assignmentEvaluator;
	}
	
}
