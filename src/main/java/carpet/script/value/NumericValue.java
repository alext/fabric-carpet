package carpet.script.value;

import carpet.script.exception.InternalExpressionException;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

public class NumericValue extends Value
{
    private final double value;
    private Long longValue;
    private static final double epsilon = abs(32 * ((7 * 0.1) * 10 - 7));
    private static final MathContext displayRounding = new MathContext(12, RoundingMode.HALF_EVEN);

    public static NumericValue asNumber(final Value v1, final String id)
    {
        if (v1 instanceof final NumericValue nv)
        {
            return nv;
        }
        throw new InternalExpressionException("Argument " + id + " has to be of a numeric type");
    }

    public static NumericValue asNumber(final Value v1)
    {
        if (v1 instanceof final NumericValue nv)
        {
            return nv;
        }
        throw new InternalExpressionException("Operand has to be of a numeric type");
    }

    public static <T extends Number> Value of(final T value)
    {
        if (value == null)
        {
            return Value.NULL;
        }
        if (value.doubleValue() == value.longValue())
        {
            return new NumericValue(value.longValue());
        }
        if (value instanceof Float)
        {
            return new NumericValue(0.000_001D * Math.round(1_000_000.0D * value.doubleValue()));
        }
        return new NumericValue(value.doubleValue());
    }


    @Override
    public String getString()
    {
        if (longValue != null)
        {
            return Long.toString(getLong());
        }
        try
        {
            if (Double.isInfinite(value))
            {
                return (value > 0) ? "INFINITY" : "-INFINITY";
            }
            if (Double.isNaN(value))
            {
                return "NaN";
            }
            if (abs(value) < epsilon)
            {
                return (signum(value) < 0) ? "-0" : "0"; //zero rounding fails with big decimals
            }
            // dobules have 16 point precision, 12 is plenty to display
            return BigDecimal.valueOf(value).round(displayRounding).stripTrailingZeros().toPlainString();
        }
        catch (final NumberFormatException exc)
        {
            throw new InternalExpressionException("Incorrect number format for " + value);
        }
    }

    @Override
    public String getPrettyString()
    {
        return longValue != null || getDouble() == getLong()
                ? Long.toString(getLong())
                : String.format(Locale.ROOT, "%.1f..", getDouble());
    }

    @Override
    public boolean getBoolean()
    {
        return abs(value) > epsilon;
    }

    public double getDouble()
    {
        return value;
    }

    public float getFloat()
    {
        return (float) value;
    }

    private static long floor(final double v)
    {
        final long invValue = (long) v;
        return v < invValue ? invValue - 1 : invValue;
    }

    public long getLong()
    {
        return longValue != null ? longValue : Long.valueOf(floor((value + epsilon)));
    }

    @Override
    public Value add(final Value v)
    {  // TODO test if definintn add(NumericVlaue) woud solve the casting
        if (v instanceof final NumericValue nv)
        {
            return new NumericValue(longValue != null && nv.longValue != null ? (longValue + nv.longValue) : (value + nv.value));
        }
        return super.add(v);
    }

    @Override
    public Value subtract(final Value v)
    {  // TODO test if definintn add(NumericVlaue) woud solve the casting
        if (v instanceof final NumericValue nv)
        {
            return new NumericValue(longValue != null && nv.longValue != null ? (longValue - nv.longValue) : (value - nv.value));
        }
        return super.subtract(v);
    }

    @Override
    public Value multiply(final Value v)
    {
        if (v instanceof final NumericValue nv)
        {
            return new NumericValue(longValue != null && nv.longValue != null ? (longValue * nv.longValue) : (value * nv.value));
        }
        return v instanceof ListValue ? v.multiply(this) : new StringValue(StringUtils.repeat(v.getString(), (int) getLong()));
    }

    @Override
    public Value divide(final Value v)
    {
        return v instanceof final NumericValue nv ? new NumericValue(getDouble() / nv.getDouble()) : super.divide(v);
    }

    @Override
    public Value clone()
    {
        return new NumericValue(value, longValue);
    }

    @Override
    public int compareTo(final Value o)
    {
        if (o.isNull())
        {
            return -o.compareTo(this);
        }
        if (o instanceof final NumericValue no)
        {
            return longValue != null && no.longValue != null ? longValue.compareTo(no.longValue) : Double.compare(value, no.value);
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public boolean equals(final Object o)
    {
        if (o instanceof final Value otherValue)
        {
            if (otherValue.isNull())
            {
                return o.equals(this);
            }
            if (o instanceof final NumericValue no)
            {
                return longValue != null && no.longValue != null ? longValue.equals(no.longValue) : !this.subtract(no).getBoolean();
            }
            return super.equals(o);
        }
        return false;
    }

    public NumericValue(final double value)
    {
        this.value = value;
    }

    private NumericValue(final double value, final Long longValue)
    {
        this.value = value;
        this.longValue = longValue;
    }

    public NumericValue(final String value)
    {
        final BigDecimal decimal = new BigDecimal(value);
        if (decimal.stripTrailingZeros().scale() <= 0)
        {
            try
            {
                longValue = decimal.longValueExact();
            }
            catch (final ArithmeticException ignored)
            {
            }
        }
        this.value = decimal.doubleValue();
    }

    public NumericValue(final long value)
    {
        this.longValue = value;
        this.value = (double) value;
    }

    @Override
    public int length()
    {
        return Long.toString(getLong()).length();
    }

    @Override
    public double readDoubleNumber()
    {
        return value;
    }

    @Override
    public long readInteger()
    {
        return getLong();
    }

    @Override
    public String getTypeString()
    {
        return "number";
    }

    @Override
    public int hashCode()
    {
        // is sufficiently close to the integer value
        return longValue != null || Math.abs(Math.floor(value + 0.5D) - value) < epsilon ? Long.hashCode(getLong()) : Double.hashCode(value);
    }


    public int getInt()
    {
        return (int) getLong();
    }

    @Override
    public Tag toTag(final boolean force)
    {
        if (longValue != null)
        {
            if (abs(longValue) < Integer.MAX_VALUE - 2)
            {
                return IntTag.valueOf((int) (long) longValue);
            }
            return LongTag.valueOf(longValue);
        }
        final long lv = getLong();
        if (value == (double) lv)
        {
            if (abs(value) < Integer.MAX_VALUE - 2)
            {
                return IntTag.valueOf((int) lv);
            }
            return LongTag.valueOf(getLong());
        }
        else
        {
            return DoubleTag.valueOf(value);
        }
    }

    @Override
    public JsonElement toJson()
    {
        if (longValue != null)
        {
            return new JsonPrimitive(longValue);
        }
        final long lv = getLong();
        return new JsonPrimitive(value == lv ? getLong() : value);
    }

    public NumericValue opposite()
    {
        return new NumericValue(longValue != null ? -longValue : -value);
    }

    public boolean isInteger()
    {
        return longValue != null || getDouble() == getLong();
    }

    public Value mod(final NumericValue n2)
    {
        if (this.longValue != null && n2.longValue != null)
        {
            return new NumericValue(Math.floorMod(longValue, n2.longValue));
        }
        final double x = value;
        final double y = n2.value;
        if (y == 0)
        {
            throw new ArithmeticException("Division by zero");
        }
        return new NumericValue(x - Math.floor(x / y) * y);
    }
}
