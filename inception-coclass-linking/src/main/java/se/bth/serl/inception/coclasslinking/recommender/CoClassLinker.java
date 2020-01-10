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

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.wicket.util.file.File;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.support.SettingsUtil;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.kb.ConceptFeatureTraits;
import de.tudarmstadt.ukp.inception.kb.KnowledgeBaseService;
import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;
import de.tudarmstadt.ukp.inception.kb.graph.KBStatement;
import de.tudarmstadt.ukp.inception.kb.model.KnowledgeBase;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.DataSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationException;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext.Key;
import se.bth.serl.inception.coclasslinking.predictor.HistoryPredictor;
import se.bth.serl.inception.coclasslinking.predictor.IPredictor;
import se.bth.serl.inception.coclasslinking.predictor.SimpleNounPredictor;
import se.bth.serl.inception.coclasslinking.predictor.Word2VecPredictor;
import se.bth.serl.inception.coclasslinking.utils.NLP;

public class CoClassLinker
    extends RecommendationEngine
{
    public static final Key<Map<String, IriFrequency>> KEY_MODEL = new Key<>("ccMapping");
    private static final String LOOKUP_FILE = "coclass-lookup.bin";
    private static final String W2V_FILE = "word2vec.bin";
    private static Map<String, List<CCObject>> coClassModel = null;
    private static Word2Vec w2vModel = null;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private List<IPredictor> predictors;
    private CoClassLinkerTraits linkerTraits;

    public CoClassLinker(Recommender aRecommender, CoClassLinkerTraits aTraits, 
            KnowledgeBaseService aKbService, FeatureSupportRegistry aFsRegistry, 
            LearningRecordService aLrService, UserDao aUserRegistry)
    {
        super(aRecommender);
        linkerTraits = aTraits;
        predictors = new ArrayList<>();

        Optional<File> mP = getModelPath();
        if (mP.isPresent()) {
            File modelPath = mP.get();

            try {
                if (coClassModel == null) {
                    coClassModel = getCoClassLookupTable(modelPath, aKbService, aFsRegistry);
                }

                if (w2vModel == null) {
                    w2vModel = WordVectorSerializer.readWord2VecModel(modelPath.file(W2V_FILE));
                }

                predictors.add(new SimpleNounPredictor(coClassModel));
                predictors.add(new Word2VecPredictor(coClassModel, w2vModel));
                predictors.add(new HistoryPredictor(coClassModel, aLrService, 
                                aRecommender.getLayer(), linkerTraits.getMaxRejects()));
                        
            }
            catch (RecommendationException e) {
                log.error(e.getMessage(), e.getCause());
            }
        }
        else {
            log.error("Could not determine CoClass model path");
        }
    }

    @Override
    public void train(RecommenderContext aContext, List<CAS> aCasses) throws RecommendationException
    {
        aContext.put(KEY_MODEL, learn(aCasses));
    }

    @Override
    public void predict(RecommenderContext aContext, CAS aCas) throws RecommendationException
    {
        try {
            aCas.setDocumentLanguage("sv");
            SimplePipeline.runPipeline(aCas, NLP.baseAnalysisEngine());

            Type predictedType = getPredictedType(aCas);
            Feature labelFeature = getPredictedFeature(aCas);
            Feature confidenceFeature = getScoreFeature(aCas);
            Feature confidenceExplanationFeature = getScoreExplanationFeature(aCas);
            Feature isPredictionFeature = getIsPredictionFeature(aCas);

            /* For some reason, we get Tokens with different POS (and possibly other varying 
             * features) JCas with select. Hence, we keep track of the terms we are interested in 
             * (nouns). Note that the hash function of Term does not consider the POS value since
             * it seems that a term can be tagged with different noun POS tags. In this way, we
             * avoid that those terms appear twice. 
             */
            Set<Term> uniqueTerms = new HashSet<>();
            
            for (Token token : JCasUtil.select(aCas.getJCas(), Token.class)) {
                Term term = new Term(token);
                if (term.isNoun() && !uniqueTerms.contains(term)) {
                    Double previousScore = Double.MAX_VALUE;
                    Map<String, Score> result = calculateScore(aContext, term);
                    for (Map.Entry<String, Score> e : result.entrySet()) {
                        String iri = e.getKey();
                        Score score = e.getValue();

                        if (!score.ignore()) {
                            // checking that results are sorted in decreasing order
                            Double avgScore = score.getAverage();
                            assert (avgScore < previousScore);
                            previousScore = avgScore;

                            AnnotationFS annotation = aCas.createAnnotation(predictedType,
                                    token.getBegin(), token.getEnd());
                            annotation.setDoubleValue(confidenceFeature, score.getAverage());
                            annotation.setStringValue(confidenceExplanationFeature,
                                    score.getExplanation());
                            annotation.setStringValue(labelFeature, iri);
                            annotation.setBooleanValue(isPredictionFeature, true);
                            aCas.addFsToIndexes(annotation);
                        }
                    }
                    
                    uniqueTerms.add(term);
                }
            }
        }
        catch (AnalysisEngineProcessException e) {
            throw new RecommendationException("Could not run analysis engine for prediction.", e);
        }
        catch (CASException e) {
            throw new RecommendationException("Could not iterate through tokens in prediction.", e);
        }
    }

    @Override
    public EvaluationResult evaluate(List<CAS> aCasses, DataSplitter aDataSplitter)
        throws RecommendationException
    {
        throw new UnsupportedOperationException("Evaluation not supported");
    }
    
    @Override
    public boolean isReadyForPrediction(RecommenderContext aContext)
    {
        return aContext.get(KEY_MODEL).map(Objects::nonNull).orElse(false);
    }

    private Optional<File> getModelPath()
    {
        File modelPath = null;
        String path = System.getProperty(SettingsUtil.getPropApplicationHome());
        if (path != null) {
            modelPath = new File(path + "/models");
            log.debug("CoClass model path: {}", modelPath.getAbsolutePath());
        }
        else {
            log.error("Could not determine CoClass model path");
        }

        return Optional.ofNullable(modelPath);
    }

    private LinkedHashMap<String, Score> calculateScore(RecommenderContext aContext, Term aTerm)
    {
        Map<String, Score> totalScore = new HashMap<>();

        // Ensure that the total score is in the range [0..1]
        double scalingFactor = 1.0 / predictors.size();

        for (IPredictor predictor : predictors) {
            Map<String, Double> intermediateScore = predictor.score(aContext, aTerm);
            intermediateScore.forEach((key, value) -> {
                value = value * scalingFactor;

                if (totalScore.containsKey(key)) {
                    totalScore.get(key).add(predictor.getName(), value);
                }
                else {
                    Score score = new Score();
                    score.add(predictor.getName(), value);
                    totalScore.put(key, score);
                }
            });
        }

        return totalScore.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry
                        .comparingByValue((e1, e2) -> e1.totalScore.compareTo(e2.totalScore))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
                        LinkedHashMap::new));

    }

    private class Score
    {
        private Map<String, Double> components;
        private Double totalScore;

        public Score()
        {
            components = new HashMap<>();
            totalScore = new Double(0);
        }

        public void add(String aPredictorName, Double aScore)
        {
            components.put(aPredictorName, aScore);
            totalScore += aScore;
        }
        
        public Double getAverage() {
            return totalScore / components.size();
        }
        
        public boolean ignore() {
            return totalScore < linkerTraits.getMinConfidence();
        }

        public String getExplanation()
        {
            return components.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .map(e -> String.format("%s: %.2f", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(" | "));

        }
    }

    private Map<String, IriFrequency> learn(List<CAS> aCasses)
    {
        Map<String, IriFrequency> model = new HashMap<>();
        
        if (aCasses.size() > 0) {
            Type annotationType = CasUtil.getType(aCasses.get(0), layerName);
            Feature labelFeature = annotationType.getFeatureByBaseName(featureName);
            for (CAS cas : aCasses) {
                for (AnnotationFS annotation : CasUtil.select(cas, annotationType)) {
                    String iri = annotation.getFeatureValueAsString(labelFeature);
                    if (isNotEmpty(iri)) {
                        String term = annotation.getCoveredText().toLowerCase();
    
                        IriFrequency iriFrequency = model.get(term);
                        if (iriFrequency != null) {
                            iriFrequency.addEntry(iri);
                        }
                        else {
                            iriFrequency = new IriFrequency(iri);
                            model.put(term, iriFrequency);
                        }
                    }
                }
            }
        }

        return model;
    }

    private Map<String, List<CCObject>> getCoClassLookupTable(File aModelPath,
            KnowledgeBaseService aKbService, FeatureSupportRegistry aFsRegistry)
        throws RecommendationException
    {
        Map<String, List<CCObject>> lookupTable = new HashMap<>();
        try {
            File modelFile = aModelPath.file(LOOKUP_FILE);
            if (modelFile.exists()) {
                log.info("Using existing coclass lookup file.");

                ObjectInputStream io = new ObjectInputStream(modelFile.inputStream());
                lookupTable = (Map<String, List<CCObject>>) io.readObject();
                io.close();
                return lookupTable;
            }
            else {
                log.info("Generating coclass lookup file...");
                try {
                    AnnotationFeature feat = recommender.getFeature();
                    FeatureSupport<ConceptFeatureTraits> fs = aFsRegistry.getFeatureSupport(feat);
                    ConceptFeatureTraits conceptFeatureTraits = fs.readTraits(feat);

                    if (conceptFeatureTraits.getRepositoryId() != null) {
                        Optional<KnowledgeBase> kb = aKbService.getKnowledgeBaseById(
                                recommender.getProject(), conceptFeatureTraits.getRepositoryId());
                        if (kb.isPresent()) {
                            List<KBHandle> handles = aKbService.listAllConcepts(kb.get(), true);
                            JCas jcas = JCasFactory.createJCas();
                            for (int i = 0; i < handles.size(); i++) {
                                KBHandle handle = handles.get(i);
                                CCObject cc = new CCObject();
                                cc.setIri(handle.getIdentifier());
                                cc.setName(handle.getName());
                                cc.setDefinition(handle.getDescription());

                                List<KBStatement> sms = aKbService.listStatements(kb.get(), handle,
                                        true);
                                for (KBStatement s : sms) {
                                    if (s.getProperty() != null && s.getProperty().getName() != null
                                            && s.getValue() != null) {
                                        if (s.getProperty().getName().equals("dimension")) {
                                            cc.setTable(s.getValue().toString());
                                        }

                                        if (s.getProperty().getName().equals("code")) {
                                            cc.setCode(s.getValue().toString());
                                        }

                                        if (s.getProperty().getName().equals("synonym")) {
                                            cc.addSynonym(s.getValue().toString());
                                        }
                                    }
                                }

                                jcas.setDocumentText(cc.getText());
                                jcas.setDocumentLanguage("sv");
                                SimplePipeline.runPipeline(jcas, NLP.baseAnalysisEngine());

                                for (Token token : JCasUtil.select(jcas, Token.class)) {
                                    Term term = new Term(token);
                                    if (term.isNoun()) {
                                        cc.addNoun(token.getStemValue().toLowerCase());
                                    }
                                }

                                jcas.reset();

                                for (String noun : cc.getNouns()) {
                                    if (lookupTable.containsKey(noun)) {
                                        lookupTable.get(noun).add(cc);
                                    }
                                    else {
                                        List<CCObject> ccobjects = new ArrayList<>();
                                        ccobjects.add(cc);
                                        lookupTable.put(noun, ccobjects);
                                    }
                                }

                                log.info("Analyzed {}/{} coclass objects.", i + 1, handles.size());
                            }

                        }
                    }
                    else {
                        log.error("Knowledgebase for feature {} not found", feat.getName());
                    }

                    ObjectOutputStream oo = new ObjectOutputStream(modelFile.outputStream());
                    oo.writeObject(lookupTable);
                    oo.close();
                }
                catch (IOException e) {
                    throw new RecommendationException("Problem reading/writing coclass data.", e);
                }
                catch (ResourceInitializationException e) {
                    throw new RecommendationException("Could not create coclass collection reader.",
                            e);
                }
                catch (UIMAException e) {
                    throw new RecommendationException("Could not run coclass model pipeline.", e);
                }

                return lookupTable;
            }
        }
        catch (IOException | ClassNotFoundException e) {
            throw new RecommendationException("Could not read coclass lookup file.", e);
        }
    }

    public static class IriFrequency
    {
        private Map<String, Integer> iris;

        public IriFrequency(String iri)
        {
            iris = new HashMap<>();
            addEntry(iri);
        }

        public void addEntry(String iri)
        {
            iris.merge(iri, 1, (oldVal, one) -> oldVal + one);
        }

        public Map<String, Integer> getEntries()
        {
            return iris;
        }
    }
}
