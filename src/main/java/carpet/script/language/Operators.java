package carpet.script.language;

import carpet.script.Context;
import carpet.script.Expression;
import carpet.script.LazyValue;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.AbstractListValue;
import carpet.script.value.BooleanValue;
import carpet.script.value.ContainerValueInterface;
import carpet.script.value.FunctionAnnotationValue;
import carpet.script.value.FunctionUnpackedArgumentsValue;
import carpet.script.value.LContainerValue;
import carpet.script.value.ListValue;
import carpet.script.value.MapValue;
import carpet.script.value.NumericValue;
import carpet.script.value.Value;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Operators
{
    public static final Map<String, Integer> precedence = new HashMap<>()
    {{
        put("attribute~:", 80);
        put("unary+-!...", 60);
        put("exponent^", 40);
        put("multiplication*/%", 30);
        put("addition+-", 20);
        put("compare>=><=<", 10);
        put("equal==!=", 7);
        put("and&&", 5);
        put("or||", 4);
        put("assign=<>", 3);
        put("def->", 2);
        put("nextop;", 1);
    }};

    public static void apply(final Expression expression)
    {
        expression.addBinaryOperator("+", precedence.get("addition+-"), true, Value::add);
        expression.addFunction("sum", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator.add(v);
            }
            return accumulator;
        });
        expression.addFunctionalEquivalence("+", "sum");

        expression.addBinaryOperator("-", precedence.get("addition+-"), true, Value::subtract);
        expression.addFunction("difference", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator.subtract(v);
            }
            return accumulator;
        });
        expression.addFunctionalEquivalence("-", "difference");

        expression.addBinaryOperator("*", precedence.get("multiplication*/%"), true, Value::multiply);
        expression.addFunction("product", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator.multiply(v);
            }
            return accumulator;
        });
        expression.addFunctionalEquivalence("*", "product");

        expression.addBinaryOperator("/", precedence.get("multiplication*/%"), true, Value::divide);
        expression.addFunction("quotient", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator.divide(v);
            }
            return accumulator;
        });
        expression.addFunctionalEquivalence("/", "quotient");

        expression.addBinaryOperator("%", precedence.get("multiplication*/%"), true, (v1, v2) ->
                NumericValue.asNumber(v1).mod(NumericValue.asNumber(v2)));
        expression.addBinaryOperator("^", precedence.get("exponent^"), false, (v1, v2) ->
                new NumericValue(java.lang.Math.pow(NumericValue.asNumber(v1).getDouble(), NumericValue.asNumber(v2).getDouble())));

        expression.addFunction("bitwise_and", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            long accumulator = NumericValue.asNumber(lv.get(0)).getLong();
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator & NumericValue.asNumber(v).getLong();
            }
            return new NumericValue(accumulator);
        });

        expression.addFunction("bitwise_xor", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            long accumulator = NumericValue.asNumber(lv.get(0)).getLong();
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator ^ NumericValue.asNumber(v).getLong();
            }
            return new NumericValue(accumulator);
        });

        expression.addFunction("bitwise_or", lv -> {
            final int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            long accumulator = NumericValue.asNumber(lv.get(0)).getLong();
            for (final Value v : lv.subList(1, size))
            {
                accumulator = accumulator | NumericValue.asNumber(v).getLong();
            }
            return new NumericValue(accumulator);
        });

        // lazy cause RHS is only conditional
        expression.addLazyBinaryOperator("&&", precedence.get("and&&"), false, true, t -> Context.Type.BOOLEAN, (c, t, lv1, lv2) ->
        { // todo check how is optimizations going
            final Value v1 = lv1.evalValue(c, Context.BOOLEAN);
            return v1.getBoolean() ? lv2 : ((cc, tt) -> v1);
        });

        expression.addPureLazyFunction("and", -1, t -> Context.Type.BOOLEAN, (c, t, lv) -> {
            final int last = lv.size() - 1;
            if (last == -1)
            {
                return LazyValue.TRUE;
            }
            for (final LazyValue l : lv.subList(0, last))
            {
                final Value val = l.evalValue(c, Context.Type.BOOLEAN);
                if (val instanceof final FunctionUnpackedArgumentsValue fuav)
                {
                    for (final Value it : fuav)
                    {
                        if (!it.getBoolean())
                        {
                            return (cc, tt) -> it;
                        }
                    }
                }
                else
                {
                    if (!val.getBoolean())
                    {
                        return (cc, tt) -> val;
                    }
                }
            }
            return lv.get(last);
        });
        expression.addFunctionalEquivalence("&&", "and");

        // lazy cause RHS is only conditional
        expression.addLazyBinaryOperator("||", precedence.get("or||"), false, true, t -> Context.Type.BOOLEAN, (c, t, lv1, lv2) ->
        {
            final Value v1 = lv1.evalValue(c, Context.BOOLEAN);
            return v1.getBoolean() ? ((cc, tt) -> v1) : lv2;
        });

        expression.addPureLazyFunction("or", -1, t -> Context.Type.BOOLEAN, (c, t, lv) -> {
            final int last = lv.size() - 1;
            if (last == -1)
            {
                return LazyValue.FALSE;
            }
            for (final LazyValue l : lv.subList(0, last))
            {
                final Value val = l.evalValue(c, Context.Type.BOOLEAN);
                if (val instanceof final FunctionUnpackedArgumentsValue fuav)
                {
                    for (final Value it : fuav)
                    {
                        if (it.getBoolean())
                        {
                            return (cc, tt) -> it;
                        }
                    }
                }
                else
                {
                    if (val.getBoolean())
                    {
                        return (cc, tt) -> val;
                    }
                }
            }
            return lv.get(last);
        });
        expression.addFunctionalEquivalence("||", "or");

        expression.addBinaryOperator("~", precedence.get("attribute~:"), true, Value::in);

        expression.addBinaryOperator(">", precedence.get("compare>=><=<"), false, (v1, v2) ->
                BooleanValue.of(v1.compareTo(v2) > 0));
        expression.addFunction("decreasing", lv -> {
            final int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (final Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) <= 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });
        expression.addFunctionalEquivalence(">", "decreasing");

        expression.addBinaryOperator(">=", precedence.get("compare>=><=<"), false, (v1, v2) ->
                BooleanValue.of(v1.compareTo(v2) >= 0));
        expression.addFunction("nonincreasing", lv -> {
            final int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (final Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) < 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });
        expression.addFunctionalEquivalence(">=", "nonincreasing");

        expression.addBinaryOperator("<", precedence.get("compare>=><=<"), false, (v1, v2) ->
                BooleanValue.of(v1.compareTo(v2) < 0));
        expression.addFunction("increasing", lv -> {
            final int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (final Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) >= 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });
        expression.addFunctionalEquivalence("<", "increasing");

        expression.addBinaryOperator("<=", precedence.get("compare>=><=<"), false, (v1, v2) ->
                BooleanValue.of(v1.compareTo(v2) <= 0));
        expression.addFunction("nondecreasing", lv -> {
            final int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (final Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) > 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });
        expression.addFunctionalEquivalence("<=", "nondecreasing");
        expression.addMathematicalBinaryIntFunction("bitwise_shift_left", (num, amount) -> num << amount);
        expression.addMathematicalBinaryIntFunction("bitwise_shift_right", (num, amount) -> num >> amount);
        expression.addMathematicalBinaryIntFunction("bitwise_roll_left", (num, num2) -> {
            final long amount = num2 % 64;
            final long amountToRoll = 64 - amount;
            final long rolledBits = ((-1L) >> amountToRoll) << amountToRoll;
            final long rolledAmount = (num & rolledBits) >> amountToRoll;
            return num << amount | rolledAmount;
        });
        expression.addMathematicalBinaryIntFunction("bitwise_roll_right", (num, num2) -> {
            final long amount = num2 % 64;
            final long amountToRoll = 64 - amount;
            final long rolledBits = ((-1L) << amountToRoll) >> amountToRoll;
            final long rolledAmount = (num & rolledBits) << amountToRoll;
            return num >> amount | rolledAmount;
        });
        expression.addMathematicalUnaryIntFunction("bitwise_not", d -> {
            final long num = (long) d;
            return ~num;
        });
        expression.addMathematicalUnaryIntFunction("bitwise_popcount", d -> (long) Long.bitCount((long)d));
        expression.addMathematicalUnaryIntFunction("double_to_long_bits", Double::doubleToLongBits);
        expression.addUnaryFunction("long_to_double_bits", v ->
                new NumericValue(Double.longBitsToDouble(NumericValue.asNumber(v).getLong())));
        expression.addBinaryOperator("==", precedence.get("equal==!="), false, (v1, v2) ->
                v1.equals(v2) ? Value.TRUE : Value.FALSE);
        expression.addFunction("equal", lv -> {
            final int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (final Value next : lv.subList(1, size))
            {
                if (!prev.equals(next))
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });
        expression.addFunctionalEquivalence("==", "equal");
        expression.addBinaryOperator("!=", precedence.get("equal==!="), false, (v1, v2) ->
                v1.equals(v2) ? Value.FALSE : Value.TRUE);
        expression.addFunction("unique", lv -> {
            final int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            // need to order them so same obejects will be next to each other.
            lv.sort(Comparator.comparingInt(Value::hashCode));
            Value prev = lv.get(0);
            for (final Value next : lv.subList(1, size))
            {
                if (prev.equals(next))
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });
        expression.addFunctionalEquivalence("!=", "unique");

        // lazy cause of assignment which is non-trivial
        expression.addLazyBinaryOperator("=", precedence.get("assign=<>"), false, false, t -> Context.Type.LVALUE, (c, t, lv1, lv2) ->
        {
            final Value v1 = lv1.evalValue(c, Context.LVALUE);
            final Value v2 = lv2.evalValue(c);
            if (v1 instanceof final ListValue.ListConstructorValue lcv && v2 instanceof final ListValue list)
            {
                final List<Value> ll = lcv.getItems();
                final List<Value> rl = list.getItems();
                if (ll.size() < rl.size())
                {
                    throw new InternalExpressionException("Too many values to unpack");
                }
                if (ll.size() > rl.size())
                {
                    throw new InternalExpressionException("Too few values to unpack");
                }
                for (final Value v : ll)
                {
                    v.assertAssignable();
                }
                final Iterator<Value> li = ll.iterator();
                final Iterator<Value> ri = rl.iterator();
                while (li.hasNext())
                {
                    final String lname = li.next().getVariable();
                    final Value vval = ri.next().reboundedTo(lname);
                    expression.setAnyVariable(c, lname, (cc, tt) -> vval);
                }
                return (cc, tt) -> Value.TRUE;
            }
            if (v1 instanceof final LContainerValue lcv)
            {
                final ContainerValueInterface container = lcv.container();
                if (container == null)
                {
                    return (cc, tt) -> Value.NULL;
                }
                final Value address = lcv.address();
                if (!(container.put(address, v2)))
                {
                    return (cc, tt) -> Value.NULL;
                }
                return (cc, tt) -> v2;
            }
            v1.assertAssignable();
            final String varname = v1.getVariable();
            final Value copy = v2.reboundedTo(varname);
            final LazyValue boundedLHS = (cc, tt) -> copy;
            expression.setAnyVariable(c, varname, boundedLHS);
            return boundedLHS;
        });

        // lazy due to assignment
        expression.addLazyBinaryOperator("+=", precedence.get("assign=<>"), false, false, t -> Context.Type.LVALUE, (c, t, lv1, lv2) ->
        {
            final Value v1 = lv1.evalValue(c, Context.LVALUE);
            final Value v2 = lv2.evalValue(c);
            if (v1 instanceof final ListValue.ListConstructorValue lcv && v2 instanceof final ListValue list)
            {
                final List<Value> ll = lcv.getItems();
                final List<Value> rl = list.getItems();
                if (ll.size() < rl.size())
                {
                    throw new InternalExpressionException("Too many values to unpack");
                }
                if (ll.size() > rl.size())
                {
                    throw new InternalExpressionException("Too few values to unpack");
                }
                for (final Value v : ll)
                {
                    v.assertAssignable();
                }
                final Iterator<Value> li = ll.iterator();
                final Iterator<Value> ri = rl.iterator();
                while (li.hasNext())
                {
                    final Value lval = li.next();
                    final String lname = lval.getVariable();
                    final Value result = lval.add(ri.next()).bindTo(lname);
                    expression.setAnyVariable(c, lname, (cc, tt) -> result);
                }
                return (cc, tt) -> Value.TRUE;
            }
            if (v1 instanceof final LContainerValue lcv)
            {
                final ContainerValueInterface cvi = lcv.container();
                if (cvi == null)
                {
                    throw new InternalExpressionException("Failed to resolve left hand side of the += operation");
                }
                final Value key = lcv.address();
                final Value value = cvi.get(key);
                if (value instanceof ListValue || value instanceof MapValue)
                {
                    ((AbstractListValue) value).append(v2);
                    return (cc, tt) -> value;
                }
                else
                {
                    final Value res = value.add(v2);
                    cvi.put(key, res);
                    return (cc, tt) -> res;
                }
            }
            v1.assertAssignable();
            final String varname = v1.getVariable();
            final LazyValue boundedLHS;
            if (v1 instanceof ListValue || v1 instanceof MapValue)
            {
                ((AbstractListValue) v1).append(v2);
                boundedLHS = (cc, tt) -> v1;
            }
            else
            {
                final Value result = v1.add(v2).bindTo(varname);
                boundedLHS = (cc, tt) -> result;
            }
            expression.setAnyVariable(c, varname, boundedLHS);
            return boundedLHS;
        });

        expression.addBinaryContextOperator("<>", precedence.get("assign=<>"), false, false, false, (c, t, v1, v2) ->
        {
            if (v1 instanceof final ListValue.ListConstructorValue lcv1 && v2 instanceof final ListValue.ListConstructorValue lcv2)
            {
                final List<Value> ll = lcv1.getItems();
                final List<Value> rl = lcv2.getItems();
                if (ll.size() < rl.size())
                {
                    throw new InternalExpressionException("Too many values to unpack");
                }
                if (ll.size() > rl.size())
                {
                    throw new InternalExpressionException("Too few values to unpack");
                }
                for (final Value v : ll)
                {
                    v.assertAssignable();
                }
                for (final Value v : rl)
                {
                    v.assertAssignable();
                }
                final Iterator<Value> li = ll.iterator();
                final Iterator<Value> ri = rl.iterator();
                while (li.hasNext())
                {
                    final Value lval = li.next();
                    final Value rval = ri.next();
                    final String lname = lval.getVariable();
                    final String rname = rval.getVariable();
                    lval.reboundedTo(rname);
                    rval.reboundedTo(lname);
                    expression.setAnyVariable(c, lname, (cc, tt) -> rval);
                    expression.setAnyVariable(c, rname, (cc, tt) -> lval);
                }
                return Value.TRUE;
            }
            v1.assertAssignable();
            v2.assertAssignable();
            final String lvalvar = v1.getVariable();
            final String rvalvar = v2.getVariable();
            final Value lval = v2.reboundedTo(lvalvar);
            final Value rval = v1.reboundedTo(rvalvar);
            expression.setAnyVariable(c, lvalvar, (cc, tt) -> lval);
            expression.setAnyVariable(c, rvalvar, (cc, tt) -> rval);
            return lval;
        });

        expression.addUnaryOperator("-", false, v -> NumericValue.asNumber(v).opposite());

        expression.addUnaryOperator("+", false, NumericValue::asNumber);

        // could be non-lazy, but who cares - its a small one.
        expression.addLazyUnaryOperator("!", precedence.get("unary+-!..."), false, true, x -> Context.Type.BOOLEAN, (c, t, lv) ->
                lv.evalValue(c, Context.BOOLEAN).getBoolean() ? (cc, tt) -> Value.FALSE : (cc, tt) -> Value.TRUE
        ); // might need context boolean

        // lazy because of typed evaluation of the argument
        expression.addLazyUnaryOperator("...", Operators.precedence.get("unary+-!..."), false, true, t -> t == Context.Type.LOCALIZATION ? Context.NONE : t, (c, t, lv) ->
        {
            if (t == Context.LOCALIZATION)
            {
                return (cc, tt) -> new FunctionAnnotationValue(lv.evalValue(c), FunctionAnnotationValue.Type.VARARG);
            }
            if (!(lv.evalValue(c, t) instanceof final AbstractListValue alv))
            {
                throw new InternalExpressionException("Unable to unpack a non-list");
            }
            final FunctionUnpackedArgumentsValue fuaval = new FunctionUnpackedArgumentsValue(alv.unpack());
            return (cc, tt) -> fuaval;
        });

    }
}
