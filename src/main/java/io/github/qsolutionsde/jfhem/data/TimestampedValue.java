package io.github.qsolutionsde.jfhem.data;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.*;

public class TimestampedValue<T extends Comparable<? super T>> implements Comparable<TimestampedValue<?>> {
    @Getter
    @Accessors(fluent = true)
    protected final T value;
    @Getter @Accessors(fluent = true)
    protected final Instant lastUpdate;

    public TimestampedValue(T value) {
        this(value,Instant.now());
    }
    public TimestampedValue(T value, Instant lastUpdate) {
        this.value = value;
        this.lastUpdate = (lastUpdate == null) ? Instant.now() : lastUpdate;
    }

    public TimestampedValue() {
        this.value = null;
        this.lastUpdate = null;
    }

    public T value(T ifInvalid) { return isValid() ? value() : ifInvalid; }

    public T value(T ifInvalid, T ifExpired, Duration maxAge)
    {
        if (isValid()) {
            if (isExpired(maxAge))
                return value();
            else
                return ifExpired;
        } else {
            return ifInvalid;
        }
    }

    public boolean isInvalid() { return value() == null; }
    public boolean isValid()   { return value() != null; }

    public Duration age()
    {
        return lastUpdate() == null ?
                ChronoUnit.FOREVER.getDuration() :
                Duration.between(lastUpdate(), Instant.now());
    }

    public boolean isExpired(Duration maxAge) {
        return !isValid() || age().compareTo(maxAge) > 0;
    }

    public boolean is(Predicate<T> test) {
        if (isInvalid())
            return false;
        else
            return test.test(value());
    }

    public boolean is(Predicate<T> test, Duration maxAge) {
        if (isInvalid() || isExpired(maxAge))
            return false;
        else
            return test.test(value());
    }

    public boolean mightBe(Predicate<T> test) {
        if (isInvalid())
            return true;
        else
            return test.test(value());
    }

    public boolean mightBe(Predicate<T> test, Duration maxAge) {
        if (isInvalid() || isExpired(maxAge))
            return true;
        else
            return test.test(value());
    }

    public void ifValid(Consumer<T> consumer) {
        if (isValid())
            consumer.accept(this.value());
    }

    public void ifValidOr(T defaultValue, Consumer<T> consumer) {
        if (isValid())
            consumer.accept(this.value());
        else
            consumer.accept(defaultValue);
    }

    public void ifValidX(Consumer<TimestampedValue<T>> consumer) {
        if (isValid())
            consumer.accept(this);
    }

    public void ifNotExpired(Duration maxAge, Consumer<T> consumer) {
        if (!isExpired(maxAge))
            consumer.accept(this.value());
    }

    public void ifNotExpiredOr(T defaultValue, Duration maxAge, Consumer<T> consumer) {
        if (! isExpired(maxAge))
            consumer.accept(this.value());
        else
            consumer.accept(defaultValue);
    }

    public void ifNotExpiredX(Duration maxAge, Consumer<TimestampedValue<T>> consumer) {
        if (isValid())
            consumer.accept(this);
    }

    // support for Optional (although not formally...)
    public boolean isPresent() { return isValid(); }
    public T orElse(T t) { return value(t); }
    public T get() { return value(); }


    public static <S extends Comparable<S>> @NonNull TimestampedValue<S> NULL() { return new TimestampedValue<>(null, null); }
    public static <V extends Comparable<V>> @NonNull TimestampedValue<V> of(TimestampedValue baseValue)
    {
        if (baseValue == null || baseValue.value() == null)
            return TimestampedValue.NULL();
        else
            return new TimestampedValue<>(baseValue.value(),baseValue.lastUpdate());
    }

    public static <S extends Comparable<S>,V extends Comparable<V>> @NonNull TimestampedValue<S> of(TimestampedValue<V> baseValue, @NonNull Function<V,S> transform)
    {
        if (baseValue == null || baseValue.value() == null)
            return TimestampedValue.NULL();
        else
            return new TimestampedValue<>(transform.apply(baseValue.value()),baseValue.lastUpdate());
    }

    public static <S extends Comparable<S>,V extends Comparable<V>, W extends Comparable<W>>
    @NonNull TimestampedValue<S> of(TimestampedValue<V> baseValue,
                                    TimestampedValue<W> baseValue2,
                                    @NonNull BiFunction<V,W,S> transform)
    {
        if (baseValue == null || baseValue2 == null || baseValue.isInvalid() || baseValue2.isInvalid())
            return TimestampedValue.NULL();
        else {
            Instant l = baseValue.lastUpdate().isBefore(baseValue2.lastUpdate()) ? baseValue.lastUpdate() : baseValue2.lastUpdate();
            return new TimestampedValue<>(transform.apply(baseValue.value(),baseValue2.value()), l);
        }
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> min(@NonNull TimestampedValue<Double>... values) {
        Optional<TimestampedValue<Double>> res = Arrays.stream(values).filter(TimestampedValue::isValid).min(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> max(@NonNull TimestampedValue<Double>... values) {
        Optional<TimestampedValue<Double>> res = Arrays.stream(values).filter(TimestampedValue::isValid).max(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> min(@NonNull final Duration maxAge, @NonNull TimestampedValue<Double>... values) {
        Optional<TimestampedValue<Double>> res = Arrays.stream(values).filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).min(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> max(@NonNull final Duration maxAge, @NonNull TimestampedValue<Double>... values) {
        Optional<TimestampedValue<Double>> res = Arrays.stream(values).filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).max(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> min(@NonNull Collection<TimestampedValue<Double>> values) {
        Optional<TimestampedValue<Double>> res = values.stream().filter(TimestampedValue::isValid).min(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> max(@NonNull Collection<TimestampedValue<Double>> values) {
        Optional<TimestampedValue<Double>> res = values.stream().filter(TimestampedValue::isValid).max(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> min(@NonNull final Duration maxAge, @NonNull Collection<TimestampedValue<Double>> values) {
        Optional<TimestampedValue<Double>> res = values.stream().filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).min(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }

    public static <S extends Comparable<S>> TimestampedValue<Double> max(@NonNull final Duration maxAge, @NonNull Collection<TimestampedValue<Double>> values) {
        Optional<TimestampedValue<Double>> res = values.stream().filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).max(Comparator.comparing(TimestampedValue::value));
        return res.orElse(TimestampedValue.NULL());
    }


    public static @NonNull TimestampedValue<Double> avg(@NonNull TimestampedValue<Double>... values) {
        OptionalDouble res = Arrays.stream(values).filter(TimestampedValue::isValid).mapToDouble(TimestampedValue::value).average();
        Optional<TimestampedValue<Double>> i = Arrays.stream(values).filter(TimestampedValue::isValid).min(Comparator.comparing(TimestampedValue::lastUpdate));
        return new TimestampedValue<>(res.isPresent() ? res.getAsDouble() : null,i.isPresent() ? i.get().lastUpdate() : Instant.now());
    }

    public static @NonNull TimestampedValue<Double> avg(@NonNull Collection<TimestampedValue<Double>> values) {
        OptionalDouble res = values.stream().filter(TimestampedValue::isValid).mapToDouble(TimestampedValue::value).average();
        Optional<TimestampedValue<Double>> i = values.stream().filter(TimestampedValue::isValid).min(Comparator.comparing(TimestampedValue::lastUpdate));
        return new TimestampedValue<>(res.isPresent() ? res.getAsDouble() : null,i.isPresent() ? i.get().lastUpdate() : Instant.now());
    }

    public static @NonNull TimestampedValue<Double> avg(@NonNull final Duration maxAge, @NonNull TimestampedValue<Double>... values) {
        OptionalDouble res = Arrays.stream(values).filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).mapToDouble(TimestampedValue::value).average();
        Optional<TimestampedValue<Double>> i = Arrays.stream(values).filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).min(Comparator.comparing(TimestampedValue::lastUpdate));
        return new TimestampedValue<>(res.isPresent() ? res.getAsDouble() : null,i.isPresent() ? i.get().lastUpdate() : Instant.now());
    }

    public static @NonNull TimestampedValue<Double> avg(@NonNull final Duration maxAge, @NonNull Collection<TimestampedValue<Double>> values) {
        OptionalDouble res = values.stream().filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).mapToDouble(TimestampedValue::value).average();
        Optional<TimestampedValue<Double>> i = values.stream().filter(t -> ! (t.isExpired(maxAge) || t.isInvalid())).min(Comparator.comparing(TimestampedValue::lastUpdate));
        return new TimestampedValue<>(res.isPresent() ? res.getAsDouble() : null,i.isPresent() ? i.get().lastUpdate() : Instant.now());
    }


    public static <S extends Comparable<S>,V extends Comparable<V>, W> @NonNull TimestampedValue<S> ofV(
            TimestampedValue<V> baseValue,
            @NonNull W baseValue2,
            @NonNull BiFunction<V,W,S> transform)
    {
        if (baseValue == null || baseValue.isInvalid())
            return TimestampedValue.NULL();
        else {
            return new TimestampedValue<>(transform.apply(baseValue.value(), baseValue2), baseValue.lastUpdate());
        }
    }

    public static <V extends Comparable<V>> @NonNull TimestampedValue<Boolean> ofBoolean(@NonNull TimestampedValue<V> baseValue, @NonNull Predicate<V> transform)
    {
        if (baseValue.isInvalid())
            return TimestampedValue.NULL();
        else
            return new TimestampedValue<>(transform.test(baseValue.value()), baseValue.lastUpdate());
    }

    public static <S extends Comparable<S>> @NonNull boolean test(TimestampedValue<S> t, Predicate<S> transform, Duration maxAge) {
        if (t.isInvalid())
            return false;

        if (t.age().compareTo(maxAge) > 0)
            return false;

        return transform.test(t.value());
    }

    public static <S extends Comparable<S>> TimestampedValue<S> firstNonExpired(Duration maxAge, TimestampedValue<S>... values) {
        for (TimestampedValue<S> t : values) {
            if (! t.isExpired(maxAge))
                return t;
        }
        return NULL();
    }

    public static <S extends Comparable<S>> TimestampedValue<S> firstNonExpired(Duration maxAge, Supplier<TimestampedValue<S>>... suppliers) {
        for (Supplier<TimestampedValue<S>> s : suppliers) {
            TimestampedValue<S> t = s.get();
            if (! t.isExpired(maxAge))
                return t;
        }
        return NULL();
    }

    public static final Predicate<Boolean> TRUE = b->b;
    public static Predicate<Boolean> FALSE = b->!b;

    public static final BiFunction<Double,Double,Boolean> GREATER_OR_EQUAL = (a, b) -> a >= b;
    public static final BiFunction<Double,Double,Boolean> GREATER = (a, b) -> a > b;
    public static final BiFunction<Double,Double,Boolean> LESS_OR_EQUAL = (a, b) -> a <= b;
    public static final BiFunction<Double,Double,Boolean> LESS = (a, b) -> a < b;

    @Override
    public int compareTo(TimestampedValue<?> o) {
        if (isValid() && o.isValid()) {
            if ( (value instanceof Long) && (o.value() instanceof Long))
                return ((Long) value()).compareTo((Long) o.value());
            if ( (value instanceof Double) && (o.value() instanceof Double))
                return ((Double) value()).compareTo((Double) o.value());
            if ( (value instanceof Boolean) && (o.value() instanceof Boolean))
                return ((Long) value()).compareTo((Long) o.value());

            return castToString().value().compareTo(o.castToString().value);
        }
        return 0;
    }

    public boolean valueEquals(TimestampedValue<T> t) {
        if (t == null)
            return false;

        if (t.isInvalid() || isInvalid())
            return false;

        return t.value().equals(value());
    }

    public static DateTimeFormatter defaultFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public static Instant instantFromString(String s) { return Instant.from(defaultFormatter.parse(s)); }

    public <S extends Comparable<S>> TimestampedValue<S> castTo(Class<S> valueType) {
        if (valueType.equals(Double.class))
            return (TimestampedValue<S>) castToDouble();
        if (valueType.equals(Long.class))
            return (TimestampedValue<S>) castToLong();
        if (valueType.equals(Boolean.class))
            return (TimestampedValue<S>) castToBoolean();
        if (valueType.equals(String.class))
            return (TimestampedValue<S>) castToString();

        return TimestampedValue.NULL();
    }

    public TimestampedValue<String> castToString() {
        if (isInvalid())
            return NULL();

        if (isString())
            return (TimestampedValue<String>) this;

        return new TimestampedValue(value.toString(),lastUpdate);
    }

    public TimestampedValue<Boolean> castToBoolean() {

        if (isBoolean())
            return (TimestampedValue<Boolean>) this;


        Boolean b = null;
        if (isDouble()) {
            if (((Double) value()) != 0)
                b = true;
            else
                b = false;
        }
        if (isLong()) {
            if (((Long) value()) != 0)
                b = true;
            else
                b = false;
        }
        if (isString()) {
            String s = (String) value();

            if (s.equalsIgnoreCase("on")
                    || s.equals("1")
                    || s.equalsIgnoreCase("true")
                    || s.equalsIgnoreCase("yes")
            )
                b = true;
            else if (s.equalsIgnoreCase("off")
                    || s.equals("0")
                    || s.equals("false")
                    || s.equals("no")
            )
                b = false;
        }
        return new TimestampedValue<>(b,lastUpdate());
    }

    public TimestampedValue<Double> castToDouble() {
        if (isInvalid())
            return NULL();

        if (isDouble())
            return (TimestampedValue<Double>) this;

        if (isBoolean())
            return TimestampedValue.of((TimestampedValue<Boolean>) this,b -> b ? 0d : 1d);

        if (isLong())
            return TimestampedValue.of((TimestampedValue<Long>) this, d -> d.doubleValue());

        if (isString()) {
            final String s = (String) value();
            if (s.contains(","))
                return TimestampedValue.of((TimestampedValue<String>) this,
                        t -> {
                            try {
                                return new DecimalFormat("#." + "########", DecimalFormatSymbols.getInstance(Locale.GERMAN)).parse(t).doubleValue();
                            } catch (ParseException e) {
                                return null;
                            }
                        });
            else
                return TimestampedValue.of((TimestampedValue<String>) this,
                        t -> {
                            try {
                                return new DecimalFormat("#." + "########", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).parse(t).doubleValue();
                            } catch (ParseException e) {
                                return null;
                            }
                        });
        }
        return TimestampedValue.NULL();
    }

    public TimestampedValue<Long> castToLong() {
        if (isInvalid())
            return NULL();

        if (isLong())
            return (TimestampedValue<Long>) this;

        if (isBoolean())
            return TimestampedValue.of((TimestampedValue<Boolean>) this,b -> b ? 0l : 1l);

        if (isDouble())
            return TimestampedValue.of((TimestampedValue<Double>) this, Math::round);

        if (isString()) {
            final String s = (String) value();
            try {
                if (s.contains(","))
                    return TimestampedValue.of((TimestampedValue<String>) this, Long::parseLong);
                else
                    return TimestampedValue.of((TimestampedValue<String>) this, Long::parseLong);
            } catch (NumberFormatException e) {
                return NULL();
            }
        }

        return TimestampedValue.NULL();
    }
    public boolean isDouble() { return value instanceof Double; }
    public boolean isBoolean() { return value instanceof Boolean; }
    public boolean isLong() { return value instanceof Long; }
    public boolean isString() { return value instanceof String; }

    public static @NonNull TimestampedValue from(@NonNull Object o, Instant i) {

        if (Double.class.isAssignableFrom(o.getClass()))
            return new TimestampedValue<>((Double) o, i);

        if (Long.class.isAssignableFrom(o.getClass()))
            return new TimestampedValue<>((Long) o, i);

        if (Boolean.class.isAssignableFrom(o.getClass()))
            return new TimestampedValue<>((Boolean) o, i);

        String s = o.toString();

        try {
            double d = Double.parseDouble(s);
            return new TimestampedValue(d,i);
        } catch (NumberFormatException e) {
            if ("true".equalsIgnoreCase(s))
                return new TimestampedValue<>(true,i);
            else if ("false".equalsIgnoreCase(s))
                return new TimestampedValue<>(false,i);
        }

        return new TimestampedValue<>(s,i);
    }

    public static TimestampedValue from(@NonNull Object o) {
        return from(o,Instant.now());
    }
}
