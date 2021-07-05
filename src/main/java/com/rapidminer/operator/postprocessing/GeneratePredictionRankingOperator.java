/**
 * Copyright (C) 2001-2020 by RapidMiner and the contributors
 * 
 * Complete list of developers available at our web site:
 * 
 * http://rapidminer.com
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
*/
package com.rapidminer.operator.postprocessing;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.example.table.NominalMapping;
import com.rapidminer.operator.AbstractExampleSetProcessing;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.UserError;
import com.rapidminer.operator.ports.metadata.ExampleSetPrecondition;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.tools.Ontology;
import com.rapidminer.tools.container.Tupel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * This operator will generate predictions of the second, .. n-th most probable class from the
 * confidences attributes generated by applying a classification model.
 * 
 * @author Sebastian Land
 */
public class GeneratePredictionRankingOperator extends AbstractExampleSetProcessing {

	public static final String PARAMETER_NUMBER_OF_RANKS = "number_of_ranks";
	public static final String PARAMETER_REMOVE_OLD_PREDICTIONS = "remove_old_predictions";

	public GeneratePredictionRankingOperator(OperatorDescription description) {
		super(description);

		getExampleSetInputPort().addPrecondition(
				new ExampleSetPrecondition(getExampleSetInputPort(), Attributes.PREDICTION_NAME, Ontology.NOMINAL));
		getExampleSetInputPort().addPrecondition(
				new ExampleSetPrecondition(getExampleSetInputPort(), Attributes.CONFIDENCE_NAME, Ontology.NUMERICAL));
	}

	@Override
	public ExampleSet apply(ExampleSet exampleSet) throws OperatorException {
		// searching confidence attributes
		Attributes attributes = exampleSet.getAttributes();
		Attribute predictedLabel = attributes.getPredictedLabel();
		if (predictedLabel == null) {
			throw new UserError(this, 107);
		}

		NominalMapping mapping = predictedLabel.getMapping();
		int numberOfLabels = mapping.size();
		Attribute[] confidences = new Attribute[numberOfLabels];
		String[] labelValue = new String[numberOfLabels];
		int i = 0;
		for (String value : mapping.getValues()) {
			labelValue[i] = value;
			confidences[i] = attributes.getConfidence(value);
			if (confidences[i] == null) {
				throw new UserError(this, 154, value);
			}
			i++;
		}

		// generating new prediction attributes
		int k = Math.min(numberOfLabels, getParameterAsInt(PARAMETER_NUMBER_OF_RANKS));
		Attribute[] kthPredictions = new Attribute[k];
		Attribute[] kthConfidences = new Attribute[k];
		for (i = 0; i < k; i++) {
			kthPredictions[i] = AttributeFactory.createAttribute(predictedLabel.getValueType());
			kthPredictions[i].setName(predictedLabel.getName() + "_" + (i + 1));
			kthPredictions[i].setMapping((NominalMapping) predictedLabel.getMapping().clone());
			kthConfidences[i] = AttributeFactory.createAttribute(Ontology.REAL);
			kthConfidences[i].setName(Attributes.CONFIDENCE_NAME + "_" + (i + 1));
			attributes.addRegular(kthPredictions[i]);
			attributes.addRegular(kthConfidences[i]);
			attributes.setSpecialAttribute(kthPredictions[i], Attributes.PREDICTION_NAME + "_" + (i + 1));
			attributes.setSpecialAttribute(kthConfidences[i], Attributes.CONFIDENCE_NAME + "_" + (i + 1));
		}
		exampleSet.getExampleTable().addAttributes(Arrays.asList(kthConfidences));
		exampleSet.getExampleTable().addAttributes(Arrays.asList(kthPredictions));

		// now setting values
		for (Example example : exampleSet) {
			ArrayList<Tupel<Double, Integer>> labelConfidences = new ArrayList<Tupel<Double, Integer>>(numberOfLabels);
			for (i = 0; i < numberOfLabels; i++) {
				labelConfidences.add(new Tupel<Double, Integer>(example.getValue(confidences[i]), i));
			}
			Collections.sort(labelConfidences);
			for (i = 0; i < k; i++) {
				Tupel<Double, Integer> tupel = labelConfidences.get(numberOfLabels - i - 1);
				example.setValue(kthPredictions[i], tupel.getSecond()); // Can use index since
																		// mapping has been cloned
																		// from above
				example.setValue(kthConfidences[i], tupel.getFirst());
			}
		}

		// deleting old prediction / confidences
		attributes.remove(predictedLabel);
		if (getParameterAsBoolean(PARAMETER_REMOVE_OLD_PREDICTIONS)) {
			for (i = 0; i < confidences.length; i++) {
				attributes.remove(confidences[i]);
			}
		}

		return exampleSet;
	}

	@Override
	public boolean writesIntoExistingData() {
		return false;
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types.add(new ParameterTypeInt(PARAMETER_NUMBER_OF_RANKS, "This determines how many ranks will be considered. ", 2,
				Integer.MAX_VALUE, false));
		types.add(new ParameterTypeBoolean(PARAMETER_REMOVE_OLD_PREDICTIONS,
				"This indicates if the old confidence attributes should be removed.", true, false));
		return types;
	}
}
