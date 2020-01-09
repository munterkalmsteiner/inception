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

package se.bth.serl.inception.coclasslinking.predictor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationException;
import se.bth.serl.inception.coclasslinking.recommender.CCObject;
import se.bth.serl.inception.coclasslinking.recommender.Term;
import se.bth.serl.inception.coclasslinking.utils.NLP;

public abstract class PredictorBase
    implements IPredictor
{
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected Map<String, List<CCObject>> coClassModel;
    private static Map<String, Optional<List<String>>> secoCache = new HashMap<>();

    public PredictorBase(Map<String, List<CCObject>> aCoClassModel)
    {
        coClassModel = aCoClassModel;
    }

    protected void logScore(Token token, Map<CCObject, Double> result)
    {
        StringBuffer msg = new StringBuffer();
        msg.append("Term: " + token.getText().toLowerCase());
        result.forEach((k, v) -> {
            msg.append(" / " + k.getName());
            Set<String> similarWords = k.getW2VSimilarWords();
            if (similarWords.size() > 0) {
                msg.append(similarWords.stream().collect(Collectors.joining(",", " [", "]")));
            }
            msg.append(" (" + v + ") ");
        });

        log.debug(msg.toString());
    }
    
    protected List<Term> decompound(Term aTerm) {
        
        List<Term> terms = new ArrayList<>();
        
        try {
            Optional<List<String>> components = querySECOS(aTerm.getTerm());
            
            if (components.isPresent()) {
                components.get().forEach(c -> {
                    try {
                        Optional<Term> term = NLP.stem(c);
                        if (term.isPresent()) {
                            terms.add(term.get());
                        }
                    } catch (RecommendationException e) {
                        log.error(e.getMessage());
                    }
                });
            }
        } catch (IOException | URISyntaxException e) {
            log.error("Could not decompound.", e);
        }
        
        
            
        return terms;
    }
    
    private Optional<List<String>> querySECOS(String compound) 
            throws IOException, URISyntaxException 
    {
        
        if (secoCache.containsKey(compound)) {
            return secoCache.get(compound); 
        }
        
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost("localhost")
                .setPath("/")
                .setPort(2020)
                .setParameter("sentence", compound)
                .build();
        
        String response = Request.Get(uri)
                .connectTimeout(1000)
                .socketTimeout(1000)
                .execute()
                .returnContent()
                .asString()
                .trim();
        
        Optional<List<String>> ret = Optional.empty();
        
        if (response != null && response.length() > 0 && response.contains(" ")) {
            String[] components = response.split(" ");
            ret = Optional.of(Arrays.asList(components));
        }
        
        secoCache.put(compound, ret);
        
        return ret;
    }  
}
