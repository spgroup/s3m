package quickdt.predictiveModels.temporallyWeightPredictiveModel; 

import com.google.common.collect.Maps; 
import quickdt.crossValidation.SimpleDateFormatExtractor; 
import quickdt.predictiveModelOptimizer.FieldValueRecommender; 
import quickdt.predictiveModelOptimizer.fieldValueRecommenders.FixedOrderRecommender; 
import quickdt.predictiveModels.PredictiveModelBuilderBuilder; 
import java.util.Map; 



public  class  TemporallyReweightedPMBuilderBuilder  implements PredictiveModelBuilderBuilder<TemporallyReweightedPM, TemporallyReweightedPMBuilder> {
	

    public static final String HALF_LIFE_OF_NEGATIVE = "halfLifeOfNegative";
	
    public static final String HALF_LIFE_OF_POSITIVE = "halfLifeOfPositive";
	

    private final PredictiveModelBuilderBuilder<?, ?> wrappedBuilderBuilder;
	

    public TemporallyReweightedPMBuilderBuilder(PredictiveModelBuilderBuilder<?, ?> wrappedBuilderBuilder) {
        this.wrappedBuilderBuilder = wrappedBuilderBuilder;
    }
	

    @Override
    public Map<String, FieldValueRecommender> createDefaultParametersToOptimize() {
        Map<String, FieldValueRecommender> parametersToOptimize = Maps.newHashMap();
        parametersToOptimize.putAll(wrappedBuilderBuilder.createDefaultParametersToOptimize());
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_2749076019014226281.java
        parametersToOptimize.put("halfLifeOfNegative", new FixedOrderRecommender(5, 10, 20, 50));
        parametersToOptimize.put("halfLifeOfPositive", new FixedOrderRecommender(5, 10, 20, 50));
=======
        parametersToOptimize.put(HALF_LIFE_OF_NEGATIVE, new FixedOrderRecommender(5.0, 10.0, 20.0));
        parametersToOptimize.put(HALF_LIFE_OF_POSITIVE, new FixedOrderRecommender(5.0, 10.0, 20.0));
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_6225507026357884592.java
        return parametersToOptimize;
    }
	

    @Override //set date time extractor to be correc
    public TemporallyReweightedPMBuilder buildBuilder(final Map<String, Object> predictiveModelConfig) {
        final double halfLifeOfPositive = (Double) predictiveModelConfig.get(HALF_LIFE_OF_POSITIVE);
        final double halfLifeOfNegative = (Double) predictiveModelConfig.get(HALF_LIFE_OF_NEGATIVE);
        return new TemporallyReweightedPMBuilder(wrappedBuilderBuilder.buildBuilder(predictiveModelConfig), new SimpleDateFormatExtractor())
                                                 .halfLifeOfNegative(halfLifeOfNegative)
                                                 .halfLifeOfPositive(halfLifeOfPositive);
    }

}

