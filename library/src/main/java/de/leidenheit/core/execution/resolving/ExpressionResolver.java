package de.leidenheit.core.execution.resolving;

public interface ExpressionResolver {

    Object resolveExpression(final String expression, final ResolverContext context);
}
