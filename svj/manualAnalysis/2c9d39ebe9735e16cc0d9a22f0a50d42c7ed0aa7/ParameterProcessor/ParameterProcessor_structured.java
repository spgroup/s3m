package io.swagger.util;

import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Example;
import io.swagger.annotations.ExampleProperty;
import io.swagger.converter.ModelConverters;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.AbstractNumericProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.FileProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.PropertyBuilder;
import io.swagger.models.properties.StringProperty;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ParameterProcessor {

    static Logger LOGGER = LoggerFactory.getLogger(ParameterProcessor.class);

    public static Parameter applyAnnotations(Swagger swagger, Parameter parameter, Type type, List<Annotation> annotations) {
        final AnnotationsHelper helper = new AnnotationsHelper(annotations, type);
        if (helper.isContext()) {
            return null;
        }
        final ParamWrapper<?> param = helper.getApiParam();
        if (param.isHidden()) {
            return null;
        }
        final String defaultValue = helper.getDefaultValue();
        if (parameter instanceof AbstractSerializableParameter) {
            final AbstractSerializableParameter<?> p = (AbstractSerializableParameter<?>) parameter;
            if (param.isRequired()) {
                p.setRequired(true);
            }
            if (StringUtils.isNotEmpty(param.getName())) {
                p.setName(param.getName());
            }
            if (StringUtils.isNotEmpty(param.getDescription())) {
                p.setDescription(param.getDescription());
            }
            if (StringUtils.isNotEmpty(param.getExample())) {
                p.setExample(param.getExample());
            }
            if (StringUtils.isNotEmpty(param.getAccess())) {
                p.setAccess(param.getAccess());
            }
            if (StringUtils.isNotEmpty(param.getDataType())) {
                if ("java.io.File".equalsIgnoreCase(param.getDataType())) {
                    p.setProperty(new FileProperty());
                } else {
                    if ("long".equalsIgnoreCase(param.getDataType())) {
                        p.setProperty(new LongProperty());
                    } else {
                        p.setType(param.getDataType());
                    }
                }
            }
            if (helper.getMin() != null || helper.getDecimalMin() != null) {
                p.setMinimum(helper.getMin() != null ? new Double(helper.getMin()) : helper.getDecimalMin());
                if (helper.isMinExclusive()) {
                    p.setExclusiveMinimum(true);
                }
            }
            if (helper.getMax() != null || helper.getDecimalMax() != null) {
                p.setMaximum(helper.getMax() != null ? new Double(helper.getMax()) : helper.getDecimalMax());
                if (helper.isMaxExclusive()) {
                    p.setExclusiveMaximum(true);
                }
            }
            if (helper.getMinItems() != null) {
                p.setMinItems(helper.getMinItems());
            }
            if (helper.getMaxItems() != null) {
                p.setMaxItems(helper.getMaxItems());
            }
            if (helper.getMinLength() != null) {
                p.setMinLength(helper.getMinLength());
            }
            if (helper.getMaxLength() != null) {
                p.setMaxLength(helper.getMaxLength());
            }
            if (helper.getPattern() != null) {
                p.setPattern(helper.getPattern());
            }
            if (helper.isRequired() != null) {
                p.setRequired(true);
            }
            if (helper.getType() != null) {
                p.setType(helper.getType());
            }
            if (helper.getFormat() != null) {
                p.setFormat(helper.getFormat());
            }
            AllowableValues allowableValues = AllowableValuesUtils.create(param.getAllowableValues());
            if (p.getItems() != null || param.isAllowMultiple()) {
                if (p.getItems() == null) {
                    final Map<PropertyBuilder.PropertyId, Object> args = new EnumMap<PropertyBuilder.PropertyId, Object>(PropertyBuilder.PropertyId.class);
                    args.put(PropertyBuilder.PropertyId.DEFAULT, p.getDefaultValue());
                    p.setDefaultValue(null);
                    args.put(PropertyBuilder.PropertyId.ENUM, p.getEnum());
                    p.setEnum(null);
                    args.put(PropertyBuilder.PropertyId.MINIMUM, p.getMinimum());
                    p.setMinimum(null);
                    args.put(PropertyBuilder.PropertyId.EXCLUSIVE_MINIMUM, p.isExclusiveMinimum());
                    p.setExclusiveMinimum(null);
                    args.put(PropertyBuilder.PropertyId.MAXIMUM, p.getMaximum());
                    p.setMaximum(null);
                    args.put(PropertyBuilder.PropertyId.EXCLUSIVE_MAXIMUM, p.isExclusiveMaximum());
                    args.put(PropertyBuilder.PropertyId.MIN_LENGTH, p.getMinLength());
                    p.setMinLength(null);
                    args.put(PropertyBuilder.PropertyId.MAX_LENGTH, p.getMaxLength());
                    p.setMaxLength(null);
                    args.put(PropertyBuilder.PropertyId.PATTERN, p.getPattern());
                    p.setPattern(null);
                    args.put(PropertyBuilder.PropertyId.EXAMPLE, p.getExample());
                    p.setExclusiveMaximum(null);
                    Property items = PropertyBuilder.build(p.getType(), p.getFormat(), args);
                    p.type(ArrayProperty.TYPE).format(null).items(items);
                }
                final Map<PropertyBuilder.PropertyId, Object> args = new EnumMap<PropertyBuilder.PropertyId, Object>(PropertyBuilder.PropertyId.class);
                if (StringUtils.isNotEmpty(defaultValue)) {
                    args.put(PropertyBuilder.PropertyId.DEFAULT, defaultValue);
                }
                if (helper.getMin() != null || helper.getDecimalMin() != null) {
                    args.put(PropertyBuilder.PropertyId.MINIMUM, helper.getMin() != null ? new Double(helper.getMin()) : helper.getDecimalMin());
                    if (helper.isMinExclusive()) {
                        args.put(PropertyBuilder.PropertyId.EXCLUSIVE_MINIMUM, true);
                    }
                }
                if (helper.getMax() != null || helper.getDecimalMax() != null) {
                    args.put(PropertyBuilder.PropertyId.MAXIMUM, helper.getMax() != null ? new Double(helper.getMax()) : helper.getDecimalMax());
                    if (helper.isMaxExclusive()) {
                        args.put(PropertyBuilder.PropertyId.EXCLUSIVE_MAXIMUM, true);
                    }
                }
                if (helper.getMinLength() != null) {
                    args.put(PropertyBuilder.PropertyId.MIN_LENGTH, helper.getMinLength());
                }
                if (helper.getMaxLength() != null) {
                    args.put(PropertyBuilder.PropertyId.MAX_LENGTH, helper.getMaxLength());
                }
                if (helper.getPattern() != null) {
                    args.put(PropertyBuilder.PropertyId.PATTERN, helper.getPattern());
                }
                if (allowableValues != null) {
                    args.putAll(allowableValues.asPropertyArguments());
                }
                PropertyBuilder.merge(p.getItems(), args);
            } else {
                if (StringUtils.isNotEmpty(defaultValue)) {
                    p.setDefaultValue(defaultValue);
                }
                if (allowableValues != null) {
                    processAllowedValues(allowableValues, p);
                }
            }
        } else {
            BodyParameter bp = new BodyParameter();
            if (helper.getApiParam() != null) {
                ParamWrapper<?> pw = helper.getApiParam();
                if (pw instanceof ApiParamWrapper) {
                    ApiParamWrapper apiParam = (ApiParamWrapper) pw;
                    Example example = apiParam.getExamples();
                    if (example != null && example.value() != null) {
                        for (ExampleProperty ex : example.value()) {
                            String mediaType = ex.mediaType();
                            String value = ex.value();
                            if (!mediaType.isEmpty() && !value.isEmpty()) {
                                bp.example(mediaType.trim(), value.trim());
                            }
                        }
                    }
                } else {
                    if (pw instanceof ApiImplicitParamWrapper) {
                        ApiImplicitParamWrapper apiParam = (ApiImplicitParamWrapper) pw;
                        Example example = apiParam.getExamples();
                        if (example != null && example.value() != null) {
                            for (ExampleProperty ex : example.value()) {
                                String mediaType = ex.mediaType();
                                String value = ex.value();
                                if (!mediaType.isEmpty() && !value.isEmpty()) {
                                    bp.example(mediaType.trim(), value.trim());
                                }
                            }
                        }
                    }
                }
            }
            bp.setRequired(param.isRequired());
            bp.setName(StringUtils.isNotEmpty(param.getName()) ? param.getName() : "body");
            if (StringUtils.isNotEmpty(param.getDescription())) {
                bp.setDescription(param.getDescription());
            }
            if (StringUtils.isNotEmpty(param.getAccess())) {
                bp.setAccess(param.getAccess());
            }
            final Property property = ModelConverters.getInstance().readAsProperty(type);
            if (property != null) {
                final Map<PropertyBuilder.PropertyId, Object> args = new EnumMap<PropertyBuilder.PropertyId, Object>(PropertyBuilder.PropertyId.class);
                if (StringUtils.isNotEmpty(defaultValue)) {
                    args.put(PropertyBuilder.PropertyId.DEFAULT, defaultValue);
                }
                bp.setSchema(PropertyBuilder.toModel(PropertyBuilder.merge(property, args)));
                for (Map.Entry<String, Model> entry : ModelConverters.getInstance().readAll(type).entrySet()) {
                    swagger.addDefinition(entry.getKey(), entry.getValue());
                }
            }
            parameter = bp;
        }
        return parameter;
    }

    private static void processAllowedValues(AllowableValues allowableValues, AbstractSerializableParameter<?> p) {
        if (allowableValues == null) {
            return;
        }
        Map<PropertyBuilder.PropertyId, Object> args = allowableValues.asPropertyArguments();
        if (args.containsKey(PropertyBuilder.PropertyId.ENUM)) {
            p.setEnum((List<String>) args.get(PropertyBuilder.PropertyId.ENUM));
        } else {
            if (args.containsKey(PropertyBuilder.PropertyId.MINIMUM)) {
                p.setMinimum((Double) args.get(PropertyBuilder.PropertyId.MINIMUM));
            }
            if (args.containsKey(PropertyBuilder.PropertyId.MAXIMUM)) {
                p.setMaximum((Double) args.get(PropertyBuilder.PropertyId.MAXIMUM));
            }
            if (args.containsKey(PropertyBuilder.PropertyId.EXCLUSIVE_MINIMUM)) {
                p.setExclusiveMinimum((Boolean) args.get(PropertyBuilder.PropertyId.EXCLUSIVE_MINIMUM) ? true : null);
            }
            if (args.containsKey(PropertyBuilder.PropertyId.EXCLUSIVE_MAXIMUM)) {
                p.setExclusiveMaximum((Boolean) args.get(PropertyBuilder.PropertyId.EXCLUSIVE_MAXIMUM) ? true : null);
            }
        }
    }

    private static void processJsr303Annotations(AnnotationsHelper helper, AbstractSerializableParameter<?> p) {
        if (helper == null) {
            return;
        }
        if (helper.getMin() != null) {
            p.setMinimum((Double) helper.getMin().doubleValue());
        }
        if (helper.getMax() != null) {
            p.setMaximum((Double) helper.getMax().doubleValue());
        }
    }

    public interface ParamWrapper<T extends Annotation> {

        String getName();

        String getDescription();

        String getDefaultValue();

        String getAllowableValues();

        boolean isRequired();

        String getAccess();

        boolean isAllowMultiple();

        String getDataType();

        String getParamType();

        T getAnnotation();

        boolean isHidden();

        String getExample();

        String getType();

        String getFormat();
    }

    /**
     * The <code>AnnotationsHelper</code> class defines helper methods for
     * accessing supported parameter annotations.
     */
    private static class AnnotationsHelper {

        private static final ApiParam DEFAULT_API_PARAM = getDefaultApiParam(null);

        private boolean context;

        private ParamWrapper<?> apiParam = new ApiParamWrapper(DEFAULT_API_PARAM);

        private String type;

        private String format;

        private String defaultValue;

        private Integer minItems;

        private Integer maxItems;

        private Boolean required;

        private Long min;

        private Double decimalMin;

        private boolean minExclusive = false;

        private Long max;

        private Double decimalMax;

        private boolean maxExclusive = false;

        private Integer minLength;

        private Integer maxLength;

        private String pattern;

        /**
         * Constructs an instance.
         *
         * @param annotations array or parameter annotations
         */
        public AnnotationsHelper(List<Annotation> annotations, Type _type) {
            String rsDefault = null;
            Size size = null;
            for (Annotation item : annotations) {
                if ("javax.ws.rs.core.Context".equals(item.annotationType().getName())) {
                    context = true;
                } else if (item instanceof ApiParam) {
                    apiParam = new ApiParamWrapper((ApiParam) item);
                } else if (item instanceof ApiImplicitParam) {
                    apiParam = new ApiImplicitParamWrapper((ApiImplicitParam) item);
                } else if ("javax.ws.rs.DefaultValue".equals(item.annotationType().getName())) {
                    try {
                        rsDefault = (String) item.getClass().getMethod("value").invoke(item);
                    } catch (Exception ex) {
                        LOGGER.error("Invocation of value method failed", ex);
                    }
                } else if (item instanceof Size) {
                    size = (Size) item;
                /**
                     * This annotation is handled after the loop, as the allow multiple field of the
                     * ApiParam annotation can affect how the Size annotation is translated
                     * Swagger property constraints
                     */
                } else if (item instanceof NotNull) {
                    required = true;
                } else if (item instanceof Min) {
                    min = ((Min) item).value();
                } else if (item instanceof Max) {
                    max = ((Max) item).value();
                } else if (item instanceof DecimalMin) {
                    DecimalMin decimalMinAnnotation = (DecimalMin) item;
                    decimalMin = new Double(decimalMinAnnotation.value());
                    minExclusive = !decimalMinAnnotation.inclusive();
                } else if (item instanceof DecimalMax) {
                    DecimalMax decimalMaxAnnotation = (DecimalMax) item;
                    decimalMax = new Double(decimalMaxAnnotation.value());
                    maxExclusive = !decimalMaxAnnotation.inclusive();
                } else if (item instanceof Pattern) {
                    pattern = ((Pattern) item).regexp();
                }
            }
            if (size != null) {
                Property property = ModelConverters.getInstance().readAsProperty(_type);
                boolean defaultToArray = apiParam != null && apiParam.isAllowMultiple();
                if (!defaultToArray && property instanceof AbstractNumericProperty) {
                    min = (long) size.min();
                    max = (long) size.max();
                } else if (!defaultToArray && property instanceof StringProperty) {
                    minLength = size.min();
                    maxLength = size.max();
                } else {
                    minItems = size.min();
                    maxItems = size.max();
                }
            }
            defaultValue = StringUtils.isNotEmpty(apiParam.getDefaultValue()) ? apiParam.getDefaultValue() : rsDefault;
            type = StringUtils.isNotEmpty(apiParam.getType()) ? apiParam.getType() : null;
            format = StringUtils.isNotEmpty(apiParam.getFormat()) ? apiParam.getFormat() : null;
        }

        private boolean isAssignableToNumber(Class<?> clazz) {
            return Number.class.isAssignableFrom(clazz) || int.class.isAssignableFrom(clazz) || short.class.isAssignableFrom(clazz) || long.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz);
        }

        /**
         * Returns a default @{@link ApiParam} annotation for parameters without it.
         *
         * @param annotationHolder a placeholder for default @{@link ApiParam}
         *                         annotation
         * @return @{@link ApiParam} annotation
         */
        private static ApiParam getDefaultApiParam(@ApiParam String annotationHolder) {
            for (Method method : AnnotationsHelper.class.getDeclaredMethods()) {
                if ("getDefaultApiParam".equals(method.getName())) {
                    return (ApiParam) method.getParameterAnnotations()[0][0];
                }
            }
            throw new IllegalStateException("Failed to locate default @ApiParam");
        }

        /**
         * Checks whether the @{@link Context} annotation is present.
         *
         * @return <code>true<code> if parameter is defined with the @{@link Context} annotation
         */
        public boolean isContext() {
            return context;
        }

        /**
         * Returns @{@link ApiParam} annotation. If no @{@link ApiParam} is present
         * a default one will be returned.
         *
         * @return @{@link ApiParam} annotation
         */
        public ParamWrapper<?> getApiParam() {
            return apiParam;
        }

        /**
         * Returns default value from annotation.
         *
         * @return default value from annotation
         */
        public String getDefaultValue() {
            return defaultValue;
        }

        public Integer getMinItems() {
            return minItems;
        }

        public Integer getMaxItems() {
            return maxItems;
        }

        public Boolean isRequired() {
            return required;
        }

        public Long getMax() {
            return max;
        }

        public Double getDecimalMax() {
            return decimalMax;
        }

        public boolean isMaxExclusive() {
            return maxExclusive;
        }

        public Long getMin() {
            return min;
        }

        public String getType() {
            return type;
        }

        public String getFormat() {
            return format;
        }

        public Double getDecimalMin() {
            return decimalMin;
        }

        public boolean isMinExclusive() {
            return minExclusive;
        }

        public Integer getMinLength() {
            return minLength;
        }

        public Integer getMaxLength() {
            return maxLength;
        }

        public String getPattern() {
            return pattern;
        }
    }

    private static final class ApiParamWrapper implements ParamWrapper<ApiParam> {

        private final ApiParam apiParam;

        private ApiParamWrapper(ApiParam apiParam) {
            this.apiParam = apiParam;
        }

        @Override
        public String getName() {
            return apiParam.name();
        }

        @Override
        public String getDescription() {
            return apiParam.value();
        }

        @Override
        public String getDefaultValue() {
            return apiParam.defaultValue();
        }

        @Override
        public String getAllowableValues() {
            return apiParam.allowableValues();
        }

        @Override
        public boolean isRequired() {
            return apiParam.required();
        }

        @Override
        public String getAccess() {
            return apiParam.access();
        }

        @Override
        public boolean isAllowMultiple() {
            return apiParam.allowMultiple();
        }

        @Override
        public String getDataType() {
            return null;
        }

        @Override
        public String getParamType() {
            return null;
        }

        @Override
        public ApiParam getAnnotation() {
            return apiParam;
        }

        @Override
        public boolean isHidden() {
            return apiParam.hidden();
        }

        @Override
        public String getExample() {
            return apiParam.example();
        }

        ;

        public Example getExamples() {
            return apiParam.examples();
        }

        public String getType() {
            return apiParam.type();
        }

        public String getFormat() {
            return apiParam.format();
        }

        ;
    }

    /**
     * Wrapper implementation for ApiImplicitParam annotation
     */
    private static final class ApiImplicitParamWrapper implements ParamWrapper<ApiImplicitParam> {

        private final ApiImplicitParam apiParam;

        private ApiImplicitParamWrapper(ApiImplicitParam apiParam) {
            this.apiParam = apiParam;
        }

        @Override
        public String getName() {
            return apiParam.name();
        }

        @Override
        public String getDescription() {
            return apiParam.value();
        }

        @Override
        public String getDefaultValue() {
            return apiParam.defaultValue();
        }

        @Override
        public String getAllowableValues() {
            return apiParam.allowableValues();
        }

        @Override
        public boolean isRequired() {
            return apiParam.required();
        }

        @Override
        public String getAccess() {
            return apiParam.access();
        }

        @Override
        public boolean isAllowMultiple() {
            return apiParam.allowMultiple();
        }

        @Override
        public String getDataType() {
            return apiParam.dataType();
        }

        @Override
        public String getParamType() {
            return apiParam.paramType();
        }

        @Override
        public ApiImplicitParam getAnnotation() {
            return apiParam;
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        @Override
        public String getExample() {
            return apiParam.example();
        }

        public Example getExamples() {
            return apiParam.examples();
        }

        public String getType() {
            return apiParam.type();
        }

        public String getFormat() {
            return apiParam.format();
        }
    }
}

