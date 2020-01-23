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

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.SPAN_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode.SINGLE_TOKEN;
import static de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode.TOKENS;
import static java.util.Arrays.asList;

import org.apache.uima.cas.CAS;
import org.apache.wicket.model.IModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.inception.kb.KnowledgeBaseService;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.AbstractTraitsEditor;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactoryImplBase;

@Component
public class CoClassLinkerFactory
    extends RecommendationEngineFactoryImplBase<CoClassLinkerTraits>
{
    public static final String ID = "se.bth.serl.inception.coclasslinking.recommender.coclassrecommender";

    private @Autowired KnowledgeBaseService kbService;
    private @Autowired FeatureSupportRegistry fsRegistry;
    private @Autowired LearningRecordService lrService;
    private @Autowired UserDao userRegistry;

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public String getName()
    {
        return "CoClass Recommender";
    }

    @Override
    public RecommendationEngine build(Recommender aRecommender)
    {
        return new CoClassLinker(aRecommender, readTraits(aRecommender), kbService, fsRegistry, 
                lrService, userRegistry);
    }

    @Override
    public boolean accepts(AnnotationLayer aLayer, AnnotationFeature aFeature)
    {
        if (aLayer == null || aFeature == null) {
            return false;
        }
        return asList(SINGLE_TOKEN, TOKENS).contains(aLayer.getAnchoringMode())
                && !aLayer.isCrossSentence() && SPAN_TYPE.equals(aLayer.getType())
                && CAS.TYPE_NAME_STRING.equals(aFeature.getType()) || aFeature.isVirtualFeature();
    }
    
    @Override
    public CoClassLinkerTraits createTraits() 
    {
        return new CoClassLinkerTraits();
    }
    
    @Override
    public AbstractTraitsEditor createTraitsEditor(String aId, IModel<Recommender> aModel)
    {
        return new CoClassLinkerTraitsEditor(aId, aModel);
    }

    @Override
    public boolean isEvaluable()
    {
        return false;
    }
}
