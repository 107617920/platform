package org.labkey.api.data.dialect;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.SqlScanner;

import java.util.Map;

public class MutatingSqlDetector
{
    private static final Logger LOG = Logger.getLogger(MutatingSqlDetector.class);

    private final String _sql;
    private final StringBuilder _firstWord = new StringBuilder();

    private State _state = State.SKIP_INITIAL_WHITESPACE;

    public MutatingSqlDetector(String sql)
    {
        _sql = sql;
    }

    private enum State
    {
        SKIP_INITIAL_WHITESPACE
        {
            @Override
            State getNextState(char c, StringBuilder firstWord)
            {
                if (Character.isWhitespace(c))
                    return this;

                firstWord.append(c);
                return READ_KEYWORD;
            }
        },
        READ_KEYWORD
        {
            @Override
            State getNextState(char c, StringBuilder firstWord)
            {
                if (Character.isWhitespace(c) || ';' == c)
                {
                    // Evaluate first keyword
                    String word = firstWord.toString();
                    Boolean mutatingWord = WORD_MUTATING_MAP.get(word);

                    if (null == mutatingWord)
                        LOG.warn("Unrecognized keyword: " + word);

                    if (Boolean.TRUE == mutatingWord)
                        return DONE;

                    // Unrecognized or not a mutating keyword - clear the first word and skip to the next statement (if present)
                    firstWord.setLength(0);
                    return SKIP_TO_NEXT_STATEMENT;
                }

                firstWord.append(c);
                return this;
            }
        },
        SKIP_TO_NEXT_STATEMENT
        {
            @Override
            State getNextState(char c, StringBuilder firstWord)
            {
                return ';' == c ? SKIP_INITIAL_WHITESPACE : this;
            }
        },
        DONE
        {
            @Override
            State getNextState(char c, StringBuilder firstWord)
            {
                throw new IllegalStateException("Shouldn't be calling getNextState()");
            }
        };

        abstract State getNextState(char c, StringBuilder firstWord);
    }

    private static final Map<String, Boolean> WORD_MUTATING_MAP = new CaseInsensitiveHashMap<>();

    static
    {
        WORD_MUTATING_MAP.putAll(Map.of(
            "ALTER", true,
            "CLUSTER", true,
            "CREATE", true,
            "DELETE", true,
            "DROP", true,
            "INSERT", true,
            "TRUNCATE", true,
            "UPDATE", true
        ));

        WORD_MUTATING_MAP.putAll(Map.of(
            "BEGIN", false,
            "DO", false,
            "END", false,
            "SELECT", false,
            "WITH", false
        ));

        // Needed for SQL Server
        WORD_MUTATING_MAP.putAll(Map.of(
            "DECLARE", false,
            "EXEC", true,
            "EXECUTE", true,
            "IF", true,   // Typically IF EXISTS followed by mutating action
            "SET", true,
            "sp_rename", true
        ));
    }

    public boolean isMutating()
    {
        // Extract the first word, ignoring all leading comments and whitespace
        SqlScanner scanner = new SqlScanner(_sql);
        scanner.scan(0, new SqlScanner.Handler()
        {
            @Override
            public boolean character(char c, int index)
            {
                _state = _state.getNextState(c, _firstWord);
                return State.DONE != _state;
            }
        });

        return _firstWord.length() > 0;
    }

    public String getFirstWord()
    {
        return _firstWord.toString();
    }
}
