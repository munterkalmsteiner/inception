/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.api.evaluation;

public class AnnotatedTokenPair
{
    private int begin;
    private int end;
    private String goldLabel;
    private String predictedLabel;

    public AnnotatedTokenPair(int aBegin, int aEnd, String aGoldLabel, String aPredictedLabel)
    {
        super();
        begin = aBegin;
        end = aEnd;
        goldLabel = aGoldLabel;
        predictedLabel = aPredictedLabel;
    }

    public int getBegin()
    {
        return begin;
    }

    public int getEnd()
    {
        return end;
    }

    public String getGoldLabel()
    {
        return goldLabel;
    }

    public String getPredictedLabel()
    {
        return predictedLabel;
    }

}
