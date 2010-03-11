package org.labkey.search.umls;

import org.labkey.api.collections.MultiValueMap;
import org.labkey.api.util.Filter;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Mar 3, 2010
 * Time: 4:59:46 PM
 */
public class RRF_Reader
{
    File metaDirectory;
    
    MultiValueMap<String,String> files = new MultiValueMap<String, String>(new HashMap<String,Collection<String>>())
    {
        @Override
        protected Collection<String> createValueCollection()
        {
            return new TreeSet<String>();
        }
    };

    public RRF_Reader(File meta) throws IOException
    {
        if (!meta.exists())
            throw new FileNotFoundException(meta.getPath());
        if (!meta.isDirectory())
            throw new IllegalArgumentException(meta.getPath());

        metaDirectory = meta;

        String[] list = metaDirectory.list();
        for (String name : list)
        {
            if (-1==name.indexOf(".RRF") || name.startsWith("."))
                continue;
            String base = name.substring(0,name.indexOf('.'));
            if (base.startsWith("MRXW_"))
                base = "MRXW";
            files.put(base,name);
        }
    }

    
    static String[] stringArray=new String[0];


    /** Iterate file returning string array */
    private static class StringReader implements Iterator<String[]>, Closeable
    {
        final BufferedReader _in;
        final long _size;
        String[] _next = null;

        StringReader(File f)
        {
            try
            {
                _size = f.length();
                _in = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                _next = readNext();
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        public boolean hasNext()
        {
            boolean ret = null != _next;
            if (!ret)
                closeQuietly(this);
            return ret;
        }

        public String[] next()
        {
            try
            {
                String[] ret = _next;
                _next = readNext();
                return ret;
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        private String[] readNext() throws IOException
        {
            String line = _in.readLine();
            return null==line ? null : StringUtils.splitPreserveAllTokens(line,'|');
        }

        public void close() throws IOException
        {
            closeQuietly(_in);
        }

        @Override
        protected void finalize() throws Throwable
        {
            close();
        }
    }



    /** Iterate file returning bound objects */
    private static class RRFReader<T> implements Iterator, Closeable
    {
        final Class _class;
        final Constructor<T> _constructor;
        final Filter<T> _filter;
        final BufferedReader _in;
        final long _size;
        T _next = null;

        RRFReader(Class c, File f, Filter<T> filter)
        {
            try
            {
                _class = c;
                _constructor = c.getConstructor(stringArray.getClass());
                _size = f.length();
                _in = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                _filter = filter;
                _next = readNext();
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
            catch (NoSuchMethodException x)
            {
                throw new RuntimeException(x);
            }
        }

        public boolean hasNext()
        {
            boolean ret = null != _next;
            if (!ret)
                closeQuietly(this);
            return ret;
        }

        public T next()
        {
            try
            {
                T ret = _next;
                _next = readNext();
                return ret;
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        private T readNext() throws IOException
        {
            Object args[] = new Object[1];
            try
            {
                String line;
                while (null != (line = _in.readLine()))
                {
                    args[0] = StringUtils.splitPreserveAllTokens(line,'|');
                    T t = _constructor.newInstance(args);
                    if (null == _filter || _filter.accept(t))
                        return t;
                }
                return null;
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
            catch (InstantiationException e)
            {
                throw new RuntimeException(e);
            }
        }

        public void close() throws IOException
        {
            closeQuietly(_in);
        }

        @Override
        protected void finalize() throws Throwable
        {
            close();
        }
    }


    static class ConcatIterator<T> implements Iterator<T>, Closeable
    {
        final LinkedList<Iterator<T>> _iterators;
        Iterator<T> _current;
        T _next;
        
        ConcatIterator(Iterator<T>...iters)
        {
            _iterators = new LinkedList<Iterator<T>>(Arrays.asList(iters));
            _current = _iterators.isEmpty() ? null : _iterators.removeFirst();
            _next = readNext();
        }
        
        public boolean hasNext()
        {
            return null != _next;
        }

        public T next()
        {
            T ret = _next;
            _next = readNext();
            return ret;
        }

        private T readNext()
        {
            while (null != _current)
            {
                if (_current.hasNext())
                    return _current.next();
                if (_current instanceof Closeable)
                    closeQuietly((Closeable)_current);
                _current = _iterators.isEmpty() ? null : _iterators.removeFirst();
            }
            return null;
        }
        
        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        public void close() throws IOException
        {
            while (null != _current)
            {
                if (_current instanceof Closeable)
                    closeQuietly((Closeable)_current);
                _current = _iterators.isEmpty() ? null : _iterators.removeFirst();
            }
        }

        @Override
        protected void finalize() throws Throwable
        {
            close();
        }
        
    }


    private static String _min(String...strs)
    {
        String min = null;
        for (String s : strs)
        {
            if (min==null || (s!=null && min.compareTo(s)>0))
                min = s;
        }
        return min;
    }
    
    
    public static class MergeIterator implements Iterator<ArrayList>, Closeable
    {
        final Iterator<ConceptName> _names;
        final Iterator<Definition> _defs;
        final Iterator<SemanticType> _types;
        ConceptName name;
        Definition def;
        SemanticType type;

        MergeIterator(Iterator<ConceptName> names, Iterator<Definition> defs, Iterator<SemanticType> types)
        {
            _names = names;
            _defs = defs;
            _types = types;
            name = _names.hasNext() ? _names.next() : null;
            def = _defs.hasNext() ? _defs.next() : null;
            type = _types.hasNext() ? _types.next() : null;
        }

        public boolean hasNext()
        {
            boolean ret = null != name || null != def || null != type;
            if (!ret)
                closeQuietly(this);
            return ret;
        }

        public ArrayList next()
        {
            String nextCUI = _min(
                    null==name ? null : name.CUI,
                    null==def ? null : def.CUI,
                    null==type ? null : type.CUI);
            assert null != nextCUI;

            ArrayList ret = new ArrayList();
            int c;
            while (null != name && 0 <= (c=nextCUI.compareTo(name.CUI)))
            {
                if (c==0)
                    ret.add(name);
                name = _names.hasNext() ? _names.next() : null;
            }
            while (null != def && 0 <= (c=nextCUI.compareTo(def.CUI)))
            {
                if (c==0)
                    ret.add(def);
                def = _defs.hasNext() ? _defs.next() : null;
            }
            while (null != type && 0 <= (c=nextCUI.compareTo(type.CUI)))
            {
                if (c==0)
                    ret.add(type);
                type = _types.hasNext() ? _types.next() : null;
            }
            return ret;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        public void close() throws IOException
        {
            if (_names instanceof Closeable)
                closeQuietly((Closeable)_names);
            if (_defs instanceof Closeable)
                closeQuietly((Closeable)_defs);
            if (_names instanceof Closeable)
                closeQuietly((Closeable)_names);
        }
    }


    Iterator iterator(String type)
    {
        ArrayList<Iterator> list = new ArrayList<Iterator>();
        Collection<String> names = files.get(type);
        if (null == names || names.isEmpty())
            return Collections.emptyList().iterator();

        for (String name : names)
        {
            File f = new File(metaDirectory,name);
            list.add(new StringReader(f));
        }
        if (list.size() == 1)
            return list.get(0);
        return new ConcatIterator((Iterator[])list.toArray(new Iterator[list.size()]));
    }


    Iterator makeIterator(String type, Class cls, Filter filter)
    {
        ArrayList<Iterator> list = new ArrayList<Iterator>();
        Collection<String> names = files.get(type);
        if (names.isEmpty())
            return Collections.emptyList().iterator();

        for (String name : names)
        {
            File f = new File(metaDirectory,name);
            list.add(new RRFReader(cls, f, filter));    
        }
        if (list.size() == 1)
            return list.get(0);
        return new ConcatIterator((Iterator[])list.toArray());
    }


    Iterator<Definition> getDefinitions(Filter filter)
    {
        return makeIterator("MRDEF",Definition.class,filter);
    }

    
    Iterator<SemanticType> getTypes(Filter filter)
    {
        return makeIterator("MRSTY", SemanticType.class, filter);
    }


    Iterator<ConceptName> getNames(Filter filter)
    {
        return makeIterator("MRCONSO", SemanticType.class, filter);
    }
    

    private static void closeQuietly(Closeable c)
    {
        if (null == c) return;
        try
        {
            c.close();
        }
        catch (IOException e)
        {
        }
    }
}
