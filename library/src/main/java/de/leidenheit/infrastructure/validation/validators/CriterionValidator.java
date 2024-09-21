package de.leidenheit.infrastructure.validation.validators;

import com.google.common.base.Strings;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import de.leidenheit.core.model.ArazzoSpecification;
import de.leidenheit.core.model.Criterion;
import de.leidenheit.infrastructure.validation.ArazzoValidationOptions;
import de.leidenheit.infrastructure.validation.ArazzoValidationResult;
import de.leidenheit.infrastructure.validation.Validator;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CriterionValidator implements Validator<Criterion> {

    public static final String LOCATION = "criterion";

    @Override
    public <C> ArazzoValidationResult validate(final Criterion criterion,
                                               final C context,
                                               final ArazzoSpecification arazzo,
                                               final ArazzoValidationOptions validationOptions) {
        var result = ArazzoValidationResult.builder().build();

        if (Strings.isNullOrEmpty(criterion.getCondition())) result.addError(LOCATION, "'condition' is mandatory");

        if (Objects.nonNull(criterion.getType())) {
            if (Criterion.CriterionType.REGEX.equals(criterion.getType())) {
                if (Strings.isNullOrEmpty(criterion.getContext())) {
                    result.addError(LOCATION, "'regex' requires 'context' to be defined");
                }
            } else if (Criterion.CriterionType.JSONPATH.equals(criterion.getType())
                    || Criterion.CriterionType.XPATH.equals(criterion.getType())
            ) {
                var criterionExpressionTypeObjectValidator = new CriterionExpressionTypeObjectValidator();
                result.merge(criterionExpressionTypeObjectValidator.validate(criterion.getExpressionTypeObject(), criterion, arazzo, validationOptions));

            }
        } else {
            result.addWarning(LOCATION, "expecting 'type' is 'simple' at this point but was null");
        }

        if (!Strings.isNullOrEmpty(criterion.getContext())) {
            var isRuntimeExpression = criterion.getContext().startsWith("$");
            if (!isRuntimeExpression) result.addWarning(LOCATION, "expected 'context' to be a runtime expression");
            // references are ignored here and will be handled by a designated reference validator
        }

        if (!Strings.isNullOrEmpty(criterion.getCondition())) {
            switch (criterion.getType()) {
                case SIMPLE:
                    if (!validateSimpleCondition(criterion.getCondition())) {
                        result.addError(LOCATION, "condition '%s' is invalid for type 'simple'".formatted(criterion.getCondition()));
                    }
                    break;
                case REGEX:
                    if (!validateRegex(criterion.getCondition())) {
                        result.addError(LOCATION, "condition '%s' is a invalid regex".formatted(criterion.getCondition()));
                    }
                    break;
                case JSONPATH:
                    if (!validateJsonPath(criterion.getCondition())) {
                        result.addError(LOCATION, "condition '%s' contains invalid JSONPath".formatted(criterion.getCondition()));
                    }
                    break;
                case XPATH:
                    if (!validateXPath(criterion.getCondition())) {
                        result.addError(LOCATION, "condition '%s' contains invalid XPath".formatted(criterion.getCondition()));
                    }
                    break;
                default:
                    break;
            }
        }

        if (Objects.nonNull(criterion.getExtensions()) && !criterion.getExtensions().isEmpty()) {
            var extensionValidator = new ExtensionsValidator();
            result.merge(extensionValidator.validate(criterion.getExtensions(), criterion, arazzo, validationOptions));
        }

        return result;
    }

    @Override
    public boolean supports(final Class<?> clazz) {
        return CriterionValidator.class.isAssignableFrom(clazz);
    }

    private boolean validateSimpleCondition(final String condition) {
        return !condition.trim().isEmpty();
    }

    private boolean validateRegex(final String condition) {
        try {
            var compiled = Pattern.compile(condition);
            return !Strings.isNullOrEmpty(compiled.pattern());
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private boolean validateJsonPath(final String condition) {
        try {
            var compiled = JsonPath.compile(condition);
            return !Strings.isNullOrEmpty(compiled.getPath());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private boolean validateXPath(final String condition) {
        try {
            var compiled = XPathFactory.newDefaultInstance().newXPath().compile(condition);
            return !Strings.isNullOrEmpty(compiled.toString());
        } catch (XPathExpressionException e) {
            return false;
        }
    }
}
