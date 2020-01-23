/*
 * Copyright 2019
 * Software Engineering Research Lab
 * Blekinge Institute of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.bth.serl.inception.coclasslinking.recommender;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.AbstractTraitsEditor;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;

public class CoClassLinkerTraitsEditor extends AbstractTraitsEditor
{
    private static final long serialVersionUID = 7333475005081889988L;
    private static final String MID_FORM = "form";
    private static final String MID_MIN_CONFIDENCE = "minConfidence";
    private static final String MID_MAX_REJECTS = "maxRejects";
    private @SpringBean RecommendationEngineFactory<CoClassLinkerTraits> toolFactory;
    private final CoClassLinkerTraits traits;
    
    public CoClassLinkerTraitsEditor(String id, IModel<Recommender> aRecommender)
    {
        super(id, aRecommender);
        traits = toolFactory.readTraits(aRecommender.getObject());
        
        Form<CoClassLinkerTraits> form = new Form<CoClassLinkerTraits>(MID_FORM,
                CompoundPropertyModel.of(Model.of(traits)))
        {
            private static final long serialVersionUID = 1142078702469607636L;

            @Override
            protected void onSubmit()
            {
                super.onSubmit();
                toolFactory.writeTraits(aRecommender.getObject(), traits);
            }
        };
        
        NumberTextField<Double> minConfidence = new NumberTextField<Double>(MID_MIN_CONFIDENCE, 
                Double.class)
                .setMinimum(0.0)
                .setMaximum(1.0)
                .setStep(0.01);
        form.add(minConfidence);
        
        NumberTextField<Integer> maxRejects = new NumberTextField<Integer>(MID_MAX_REJECTS, 
                Integer.class)
                .setMinimum(0)
                .setMaximum(100)
                .setStep(1);
        
        form.add(minConfidence);
        form.add(maxRejects);
        add(form);
    }

}
