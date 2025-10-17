package com.rits.cloning;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author kostantinos.kougios
 *
 * 21 May 2009
 */
public class FastClonerArrayList implements IFastCloner
{
	@SuppressWarnings({ "unchecked", "rawtypes" })
    public Object clone(final Object t, final IDeepCloner cloner, final Map<Object, Object> clones) {
		ArrayList al = (ArrayList) t;
		int size = al.size();
		ArrayList l = new ArrayList(size);
        for (Object o : al) {
            l.add(cloner.deepClone(o, clones));
        }
		return l;
	}

}
