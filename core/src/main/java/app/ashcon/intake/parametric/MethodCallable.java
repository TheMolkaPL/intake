/*
 * Intake, a command processing library
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) Intake team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.ashcon.intake.parametric;

import static com.google.common.base.Preconditions.checkNotNull;

import app.ashcon.intake.Command;
import app.ashcon.intake.CommandCallable;
import app.ashcon.intake.Description;
import app.ashcon.intake.ImmutableDescription;
import app.ashcon.intake.InvocationCommandException;
import app.ashcon.intake.argument.Namespace;
import app.ashcon.intake.parametric.handler.InvokeListener;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The implementation of a {@link CommandCallable} for the
 * {@link ParametricBuilder}.
 */
final class MethodCallable extends AbstractParametricCallable {

    private final Object object;
    private final Method method;
    private final Description description;

    private MethodCallable(ParametricBuilder builder, ArgumentParser parser, Object object, Method method,
                           Description description) {
        super(builder, parser);
        this.object = object;
        this.method = method;
        this.description = description;
    }

    static MethodCallable create(ParametricBuilder builder, Object object, Method method) throws IllegalParameterException {
        checkNotNull(builder, "builder");
        checkNotNull(object, "object");
        checkNotNull(method, "method");

        Set<Annotation> commandAnnotations = ImmutableSet.copyOf(method.getAnnotations());

        Command definition = method.getAnnotation(Command.class);
        checkNotNull(definition, "Method lacks a @Command annotation");

        boolean ignoreUnusedFlags = definition.anyFlags();
        Set<Character> unusedFlags = ImmutableSet.copyOf(Chars.asList(definition.flags().toCharArray()));

        Annotation[][] annotations = method.getParameterAnnotations();
        Type[] types = method.getGenericParameterTypes();

        ArgumentParser.Builder parserBuilder = new ArgumentParser.Builder(builder.getInjector());
        for (int i = 0; i < types.length; i++) {
            parserBuilder.addParameter(types[i], Arrays.asList(annotations[i]));
        }
        ArgumentParser parser = parserBuilder.build();

        String shortDes = !definition.desc().isEmpty() ? definition.desc() : null;
        ImmutableDescription.Builder descBuilder = new ImmutableDescription.Builder()
                                                       .setParameters(parser.getUserParameters())
                                                       .setShortDescription(shortDes)
                                                       .setHelp(!definition.help().isEmpty() ? definition.help() : shortDes)
                                                       .setUsageOverride(
                                                           !definition.usage().isEmpty() ? definition.usage() : null)
                                                       .setPermissions(Arrays.asList(definition.perms()));

        for (InvokeListener listener : builder.getInvokeListeners()) {
            listener.updateDescription(commandAnnotations, parser, descBuilder);
        }

        Description description = descBuilder.build();

        MethodCallable callable = new MethodCallable(builder, parser, object, method, description);
        callable.setCommandAnnotations(ImmutableList.copyOf(method.getAnnotations()));
        callable.setIgnoreUnusedFlags(ignoreUnusedFlags);
        callable.setUnusedFlags(unusedFlags);
        return callable;
    }

    @Override
    protected void call(Object[] args) throws Exception {
        try {
            method.invoke(object, args);
        }
        catch (IllegalAccessException e) {
            throw new InvocationCommandException("Could not invoke method '" + method + "'", e);
        }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            else {
                throw new InvocationCommandException("Could not invoke method '" + method + "'", e);
            }
        }
    }

    @Override
    public Description getDescription() {
        return description;
    }

    @Override
    public boolean testPermission(Namespace namespace) {
        List<String> permissions = getDescription().getPermissions();
        if (permissions.isEmpty()) {
            return true;
        }
        for (String perm : permissions) {
            if (getBuilder().getAuthorizer().testPermission(namespace, perm)) {
                return true;
            }
        }
        return false;
    }

}
