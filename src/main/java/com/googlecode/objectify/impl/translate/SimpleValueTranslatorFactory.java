package com.googlecode.objectify.impl.translate;

import com.google.cloud.datastore.Value;
import com.google.cloud.datastore.ValueType;
import com.googlecode.objectify.impl.Path;

/**
 * Simplest base class for most value translations. Easy to subclass.
 *
 * @author Jeff Schnitzer <jeff@infohazard.org> 
 */
abstract public class SimpleValueTranslatorFactory<P, D> extends ValueTranslatorFactory<P, D>
{
	private final ValueType datastoreValueType;

	/** */
	public SimpleValueTranslatorFactory(final Class<? extends P> pojoType, final ValueType datastoreValueType) {
		super(pojoType);
		this.datastoreValueType = datastoreValueType;
	}

	abstract protected P toPojo(final Value<D> value);
	abstract protected Value<D> toDatastore(final P value);
	
	@Override
	final protected ValueTranslator<P, D> createValueTranslator(final TypeKey<P> tk, final CreateContext ctx, final Path path) {
		return new ValueTranslator<P, D>(datastoreValueType) {
			@Override
			protected P loadValue(final Value<D> value, final LoadContext ctx, final Path path) throws SkipException {
				return toPojo(value);
			}

			@Override
			protected Value<D> saveValue(final P value, final boolean index, final SaveContext ctx, final Path path) throws SkipException {
				return toDatastore(value);
			}
		};
	}
}