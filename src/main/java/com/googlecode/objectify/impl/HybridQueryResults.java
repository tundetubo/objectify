package com.googlecode.objectify.impl;

import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.QueryResults;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Result;
import com.googlecode.objectify.util.IterateFunction;
import lombok.Getter;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Converts keys-only query results into hybrid query results. This involves chunking the keys into batches and loading
 * each from the datastore. Care is taken to preserve cursor behavior and filter null results (possible due to both
 * the time delay between the query and the load and also eventual consistency in general).
 */
public class HybridQueryResults<T> implements QueryResults<T> {

	/** */
	private final LoadEngine loadEngine;

	/** */
	private final Iterator<ResultWithCursor<T>> stream;

	/** Track the values for the next time we need to get this */
	@Getter
	private Cursor cursorAfter;

	/**
	 * @param chunkSize can be MAX_VALUE to indicate "just one chunk" 
	 */
	public HybridQueryResults(
			final LoadEngine loadEngine,
			final QueryResults<Key<T>> source,
			final int chunkSize) {

		this.loadEngine = loadEngine;

		// Always start with whatever was in the source to begin with
		this.cursorAfter = source.getCursorAfter();

		// Turn the result in to {key, cursor} pairs
		final Iterator<ResultWithCursor<Key<T>>> withCursor = new ResultWithCursor.Iterator<>(source);

		// Break it into chunks
		final Iterator<Iterator<ResultWithCursor<Key<T>>>> chunked = safePartition(withCursor, chunkSize);
		
		// Load each chunk as a batch
		final Iterator<Iterator<ResultWithCursor<T>>> loaded = Iterators.transform(chunked, this::load);
		
		// Put the chunks back into a linear stream
		final Iterator<ResultWithCursor<T>> concatenated = Iterators.concat(loaded);
		
		// Filter out any null results
		this.stream = Iterators.filter(concatenated, rwc -> rwc.getResult() != null);
	}

	/** Detects Integer.MAX_VALUE and prevents OOM exceptions */
	private <T> Iterator<Iterator<T>> safePartition(final Iterator<T> input, final int chunkSize) {
		// Iterators.partition() allocates lists with capacity of whatever batch size you pass in; if batch
		// size is unlimited, we end up trying to allocate maxint.
		return (chunkSize == Integer.MAX_VALUE)
				? Iterators.singletonIterator(input)
				: Iterators.transform(Iterators.partition(input, chunkSize), IterateFunction.instance());
	}

	/** Loads them; note that it's possible for some loaded results to be null */
	private Iterator<ResultWithCursor<T>> load(final Iterator<ResultWithCursor<Key<T>>> keys) {
		final List<Entry<ResultWithCursor<Key<T>>, Result<T>>> results = Lists.newArrayList();

		while (keys.hasNext()) {
			final ResultWithCursor<Key<T>> next = keys.next();
			results.add(Maps.immutableEntry(next, loadEngine.load(next.getResult())));
		}

		loadEngine.execute();

		return Iterators.transform(results.iterator(), entry -> new ResultWithCursor<>(entry.getValue().now(), entry.getKey().getCursorAfter()));
	}

	@Override
	public boolean hasNext() {
		return stream.hasNext();
	}

	@Override
	public T next() {
		final ResultWithCursor<T> result = stream.next();
		cursorAfter = result.getCursorAfter();

		return result.getResult();
	}

	/** Not implemented */
	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Class<?> getResultClass() {
		// Not really possible to do this; a query could produce anything
		return Object.class;
	}
}