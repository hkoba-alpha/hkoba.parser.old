package hkoba.parser.analyze;

import hkoba.parser.TokenData;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@RequiredArgsConstructor
public class SyntaxArgs {
    public class ArgsProcess {
        private List<Class[]> argClass = new ArrayList<>();
        private List<Function<Object[], Object>> resolverList = new ArrayList<>();

        public <T, R> ArgsProcess process(Class<T> arg1Class, Function<T, R> proc) {
            argClass.add(new Class[]{arg1Class});
            resolverList.add(arg -> proc.apply((T) arg[0]));
            return this;
        }

        public <T, U, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, Function<T, Function<U, R>> proc) {
            argClass.add(new Class[]{arg1Class, arg2Class});
            resolverList.add(arg -> proc.apply((T) arg[0]).apply((U) arg[1]));
            return this;
        }

        public <T, U, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, BiFunction<T, U, R> proc) {
            argClass.add(new Class[]{arg1Class, arg2Class});
            resolverList.add(arg -> proc.apply((T) arg[0], (U) arg[1]));
            return this;
        }

        public <T, U, V, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, Class<V> arg3Class, Function<T, Function<U, Function<V, R>>> proc) {
            argClass.add(new Class[]{arg1Class, arg2Class, arg3Class});
            resolverList.add(arg -> proc.apply((T) arg[0]).apply((U) arg[1]).apply((V) arg[2]));
            return this;
        }


        public <T, U, V, W, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, Class<V> arg3Class, Class<W> arg4Class, Function<T, Function<U, Function<V, Function<W, R>>>> proc) {
            argClass.add(new Class[]{arg1Class, arg2Class, arg3Class, arg4Class});
            resolverList.add(arg -> proc.apply((T) arg[0]).apply((U) arg[1]).apply((V) arg[2]).apply((W) arg[3]));
            return this;
        }

        public <R> Optional<R> execute() {
            int bakIndex = index;
            for (int i = 0; i < argClass.size(); i++) {
                index = 0;
                Class[] types = argClass.get(i);
                Object[] args = new Object[types.length];
                boolean ngFlag = false;
                for (int j = 0; j < types.length; j++) {
                    Optional arg = getValue(types[j]);
                    if (!arg.isPresent()) {
                        ngFlag = true;
                        break;
                    }
                    args[j] = arg.get();
                }
                if (!ngFlag) {
                    // 完成
                    index = bakIndex;
                    return Optional.ofNullable((R) resolverList.get(i).apply(args));
                }
            }
            index = bakIndex;
            return Optional.empty();
        }
    }

    private final List<TokenData> childArgs;
    private int index = 0;

    public <T, U extends TokenData<T>> Optional<U> getToken(int ix) {
        if (ix < 0 || ix >= childArgs.size()) {
            return Optional.empty();
        }
        return Optional.of((U) childArgs.get(ix));
    }

    public <T> Optional<T> getValue(int ix) {
        return getToken(ix).flatMap(v -> Optional.ofNullable((T) v.getValue()));
    }

    public <T, U extends TokenData<T>> Optional<U> getNode(TokenData.TokenType type) {
        for (int ix = index; ix < childArgs.size(); ix++) {
            if (childArgs.get(ix).getType().equals(type)) {
                index = ix + 1;
                return Optional.of((U) childArgs.get(ix));
            }
        }
        return Optional.empty();
    }

    public <T> Optional<T> getValue(TokenData.TokenType type) {
        return getNode(type).flatMap(v -> Optional.ofNullable((T) v.getValue()));
    }

    public <T> Optional<T> getValue(Class<T> valueClass) {
        for (int ix = index; ix < childArgs.size(); ix++) {
            if (valueClass.isInstance(childArgs.get(ix).getValue())) {
                index = ix + 1;
                return Optional.of((T) childArgs.get(ix).getValue());
            }
        }
        return Optional.empty();
    }

    public <T, R> ArgsProcess process(Class<T> arg1Class, Function<T, R> proc) {
        return new ArgsProcess().process(arg1Class, proc);
    }

    public <T, U, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, Function<T, Function<U, R>> proc) {
        return new ArgsProcess().process(arg1Class, arg2Class, proc);
    }

    public <T, U, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, BiFunction<T, U, R> proc) {
        return new ArgsProcess().process(arg1Class, arg2Class, proc);
    }

    public <T, U, V, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, Class<V> arg3Class, Function<T, Function<U, Function<V, R>>> proc) {
        return new ArgsProcess().process(arg1Class, arg2Class, arg3Class, proc);
    }

    public <T, U, V, W, R> ArgsProcess process(Class<T> arg1Class, Class<U> arg2Class, Class<V> arg3Class, Class<W> arg4Class, Function<T, Function<U, Function<V, Function<W, R>>>> proc) {
        return new ArgsProcess().process(arg1Class, arg2Class, arg3Class, arg4Class, proc);
    }
}
