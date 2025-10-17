package com.rits.tests.cloning;

import com.rits.cloning.Cloner;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * test thread safety of cloner
 * 
 * @author kostantinos.kougios
 *
 * 18 Jan 2009
 */
public class TestClonerThreadSafety
{
	private static final Cloner	cloner	= new Cloner(); // use 1 cloner for all tests (all threads)

	@Execution(ExecutionMode.CONCURRENT)
	@RepeatedTest(80)
	public void testCloner()
	{
		final Random r = new Random();
		for (int i = 0; i < 10000; i++)
		{
			final Calendar cal = Calendar.getInstance();
			final Calendar clone = cloner.deepClone(cal);
			assertNotSame(cal, clone);
			assertNotSame(cal.getTime(), clone.getTime());
            assertEquals(cal, clone);

			if (r.nextBoolean())
			{
				Thread.yield();
			}
			final List<Calendar> l = new ArrayList<Calendar>();
			l.add(cal);
			l.add(Calendar.getInstance());
			final List<Calendar> lClone = cloner.deepClone(l);
			assertNotSame(l, lClone);
			assertEquals(l.size(), lClone.size());
			assertEquals(l.get(0), lClone.get(0));
			assertEquals(l.get(1), lClone.get(1));
			if (r.nextBoolean())
			{
				Thread.yield();
			}
			try
			{
				final URL url = new URL("http://localhost");
				assertEquals(url, cloner.deepClone(url));
			} catch (final MalformedURLException e)
			{
				throw new RuntimeException(e);
			}
			final TreeMap<String, Object> m = new TreeMap<String, Object>();
			m.put("cal", cal);
			m.put("clone", clone);
			final TreeMap<String, Object> dm = cloner.deepClone(m);
			assertEquals(m.size(), dm.size());
			assertNotSame(m.get("cal"), dm.get("cal"));
			assertEquals(m.get("cal"), dm.get("cal"));
			assertNotSame(m.get("clone"), dm.get("clone"));
			assertEquals(m.get("clone"), dm.get("clone"));
			if (r.nextBoolean())
			{
				Thread.yield();
			}
		}
	}
}
