grammar SqlBase;

options
{
    language=Java;
    output=AST;
    memoize=true;
    backtrack=true; // primaryExpression : OPEN! ( subQuery | expression ) CLOSE!
    ASTLabelType=CommonTree;
}

//
// SQL language grammar.
// This source code was modified from the Hibernate project ('hql.g').
//

tokens
{
	AGGREGATE;		// One of the aggregate functions (e.g. min, max, avg)
	ALIAS;
	EXPR_LIST;
	FILTER_ENTITY;		// FROM element injected because of a filter expression (happens during compilation phase 2)
	IN_LIST;
	IS_NOT;
	METHOD_CALL;
	NOT_BETWEEN;
	NOT_IN;
	NOT_LIKE;
	ORDER_ELEMENT;
	QUERY;
	RANGE;
	ROW_STAR;
	SELECT_FROM;
	UNARY_MINUS;
	UNARY_PLUS;
	UNION_ALL;
	VECTOR_EXPR;		// ( x, y, z )
	WEIRD_IDENT;		// Identifiers that were keywords when they came in.
}


@header
{
	package org.labkey.query.sql.antlr;
}


@lexer::header
{
	package org.labkey.query.sql.antlr;
}


@members
{
    /**
     * This method looks ahead and converts . <token> into . IDENT when
     * appropriate.
     */
    public void handleDotIdent() //throws TokenStreamException
    {
    }

	public void weakKeywords() //throws TokenStreamException
	{
	}
}


@lexer::members
{
    protected void setPossibleID(boolean possibleID)
    {
    }
}


//
// SQL TOKENS
//

ALL : 'all';
ANY : 'any';
AND : 'and';
AS : 'as';
ASCENDING : 'asc';
AVG : 'avg';
BETWEEN : 'between';
CASE : 'case';
CAST : 'cast';
CLASS : 'class';
COUNT : 'count';
DELETE : 'delete';
DESCENDING : 'desc';
DISTINCT : 'distinct';
DOT : '.';
ELEMENTS : 'elements';
ELSE : 'else';
END : 'end';
ESCAPE : 'escape';
EXISTS : 'exists';
FALSE : 'false';
FETCH : 'fetch';
FROM : 'from';
FULL : 'full';
GROUP : 'group';
HAVING : 'having';
IN : 'in';
INDICES : 'indices';
INNER : 'inner';
INSERT : 'insert';
INTO : 'into';
IS : 'is';
JOIN : 'join';
LEFT : 'left';
LIKE : 'like';
LIMIT : 'limit';
MAX : 'max';
GROUP_CONCAT : 'group_concat';
MIN : 'min';
NEW : 'new';
NOT : 'not';
NULL : 'null';
ON : 'on';
OR : 'or';
ORDER : 'order';
OUTER : 'outer';
RIGHT : 'right';
SELECT : 'select';
SET : 'set';
SOME : 'some';
STDDEV : 'stddev';
SUM : 'sum';
THEN : 'then';
TRUE : 'true';
UNION : 'union';
UPDATE : 'update';
VERSIONED : 'versioned';
WHERE : 'where';
WHEN : 'when';
// -- EJBQL tokens --
BOTH : 'both';
EMPTY : 'empty';
LEADING : 'leading';
MEMBER : 'member';
OF : 'of';
TRAILING : 'trailing';


//
// SQL GRAMMAR
//


statement
	: ( updateStatement | deleteStatement | selectStatement | insertStatement )
	;

updateStatement
	: UPDATE^ (VERSIONED)?
		optionalFromTokenFromClause
		setClause
		(whereClause)?
	;

setClause
	: (SET^ assignment (COMMA! assignment)*)
	;

assignment
	: stateField EQ^ newValue
	;

// 'state_field' is the term used in the EJB3 sample grammar; used here for easy reference.
// it is basically a property ref
stateField
	: path
	;

// this still needs to be defined in the ejb3 spec; additiveExpression is currently just a best guess,
// although it is highly likely I would think that the spec may limit this even more tightly.
newValue
	: concatenation
	;

deleteStatement
	: DELETE^
		(optionalFromTokenFromClause)
		(whereClause)?
	;

optionalFromTokenFromClause!
	: (FROM)? f=path (a=asAlias)?
	    -> ^(FROM $f $a)
	;

selectStatement
	: q=union
		((o=orderByClause! {$q.tree.addChild($o.tree);})?)
		((l=limitClause! {$q.tree.addChild($l.tree);})?)
	;

insertStatement
	// Would be nice if we could abstract the FromClause/FromElement logic
	// out such that it could be reused here; something analogous to
	// a 'table' rule in sql-grammars
	: INSERT^ intoClause select
	;

intoClause
	: INTO^ path { weakKeywords(); } insertablePropertySpec
	;

insertablePropertySpec
	: OPEN! primaryExpression ( COMMA! primaryExpression )* CLOSE!
	;


union
  : unionTerm (u=UNION^ (ALL! { $u.tree.getToken().setType(UNION_ALL); } )? unionTerm)*
  ;


unionTerm
  : select
  | OPEN! union CLOSE!
  ;


select
	: (selectFrom (whereClause)? (groupByClause (havingClause)?)?)
	    -> ^(QUERY selectFrom whereClause? groupByClause? havingClause?)
    ;


selectFrom!
	: (selectClause fromClause?)
	    -> ^(SELECT_FROM fromClause? selectClause)
	;


selectClause
	: SELECT^ { weakKeywords(); }	// Weak keywords can appear immediately after a SELECT token.
		(DISTINCT)? ( selectedPropertiesList )
	;


// NOTE: This *must* begin with the 'FROM' token, otherwise the sub-query rule will be ambiguous
// with the expression rule.
// Also note: after a comma weak keywords are allowed and should be treated as identifiers.

fromClause
	: FROM^ { weakKeywords(); } fromRange ( fromJoin | COMMA! { weakKeywords(); } fromRange )*
	;

fromJoin
	: ( ( (LEFT|RIGHT|FULL) (OUTER!)? )  | INNER )? JOIN^ (FETCH)?
	  (( path (asAlias)?) |
	  ((OPEN! subQuery CLOSE!) asAlias)) onClause?
	;

onClause
	: ON^ logicalExpression
	;

fromRange
	: fromClassOrOuterQueryPath
	| aliasedSubQuery
	;


fromClassOrOuterQueryPath!
	: (path { weakKeywords(); } asAlias?) 
        -> ^(RANGE path asAlias?)
	;


aliasedSubQuery!
    : (OPEN subQuery CLOSE asAlias)
        -> ^(RANGE subQuery asAlias)
    ;


// Alias rule - Parses the optional 'as' token and forces an AST identifier node.
asAlias
	: (AS!)? alias
	;

alias
	: identifier
    ;

//## groupByClause:
//##     GROUP_BY path ( COMMA path )*;

groupByClause
	: GROUP^ 'by'! expression ( COMMA! expression )*
	;


orderByClause
	: ORDER^ 'by'! orderElement ( COMMA! orderElement )*
	;


limitClause
    : LIMIT^ NUM_INT;


orderElement
	: expression ( ascendingOrDescending )?
	;


ascendingOrDescending
	: ( 'asc' | 'ascending' )   -> ^(ASCENDING)
	| ( 'desc' | 'descending') 	-> ^(DESCENDING)
	;


havingClause
	: HAVING^ logicalExpression
	;


whereClause
	: WHERE^ logicalExpression
	;


selectedPropertiesList
	: aliasedSelectExpression ( COMMA! aliasedSelectExpression )*
	;


aliasedSelectExpression
	: (expression ( AS^ identifier )?)
	| starAtom
	;


// expressions
// Note that most of these expressions follow the pattern
//   thisLevelExpression :
//       nextHigherPrecedenceExpression
//           (OPERATOR nextHigherPrecedenceExpression)*
// which is a standard recursive definition for a parsing an expression.
//
// Operator precedence in HQL
// lowest  --> ( 7)  OR
//             ( 6)  AND, NOT
//             ( 5)  equality: ==, <>, !=, is
//             ( 4)  relational: <, <=, >, >=,
//                   LIKE, NOT LIKE, BETWEEN, NOT BETWEEN, IN, NOT IN
//             ( 3)  addition and subtraction: +(binary) -(binary)
//             ( 2)  multiplication: * / %, concatenate: ||
// highest --> ( 1)  +(unary) -(unary)
//                   []   () (method call)  . (dot -- identifier qualification)
//                   aggregate function
//                   ()  (explicit parenthesis)
//
// Note that the above precedence levels map to the rules below...
// Once you have a precedence chart, writing the appropriate rules as below
// is usually very straightfoward

logicalExpression
	: expression
	;

// Main expression rule
expression
	: logicalOrExpression
	;

// level 7 - OR
logicalOrExpression
	: logicalAndExpression ( OR^ logicalAndExpression )*
	;

// level 6 - AND, NOT
logicalAndExpression
	: negatedExpression ( AND^ negatedExpression )*
	;

negatedExpression
	 // Weak keywords can appear in an expression, so look ahead.
	: NOT^ negatedExpression
	| { weakKeywords(); } bitwiseOrExpression
	;

bitwiseOrExpression
    : equalityExpression ( (BIT_OR^ | BIT_XOR^) equalityExpression ) *
    ;

//## OP: EQ | LT | GT | LE | GE | NE | SQL_NE | LIKE;

// level 5 - EQ, NE
equalityExpression
	: relationalExpression (
		( EQ^
		| is=IS^ (NOT!  { $is.tree.getToken().setType(IS_NOT); } )?
		| NE^
		| ne=SQL_NE^ { $ne.tree.getToken().setType(NE); }
		)
      relationalExpression)?
	;

// level 4 - LT, GT, LE, GE, LIKE, NOT LIKE, BETWEEN, NOT BETWEEN
// NOTE: The NOT prefix for LIKE and BETWEEN will be represented in the
// token type.  When traversing the AST, use the token type, and not the
// token text to interpret the semantics of these nodes.


relationalExpression
	: concatenation (
		( ( (LT|GT|LE|GE)^ additiveExpression )? )
		| (n=NOT!)? (
			// Represent the optional NOT prefix using the token type by
			// testing 'n' and setting the token type accordingly.
			(i=IN^ {
					$i.tree.getToken().setType( ($n == null) ? IN : NOT_IN);
					$i.tree.getToken().setText( ($n == null) ? "in" : "not in");
				}
				inList)
			| (b=BETWEEN^ {
					$b.tree.getToken().setType( ($n == null) ? BETWEEN : NOT_BETWEEN);
					$b.tree.getToken().setText( ($n == null) ? "between" : "not between");
				}
				betweenList )
			| (l=LIKE^ {
					$l.tree.getToken().setType( ($n == null) ? LIKE : NOT_LIKE);
					$l.tree.getToken().setText( ($n == null) ? "like" : "not like");
				}
				concatenation likeEscape)
            )
		)
	;

likeEscape
	: (ESCAPE^ concatenation)?
	;

inList
	: compoundExpr -> ^(IN_LIST compoundExpr)
	;

betweenList
	: concatenation AND! concatenation
	;

//level 4 - string concatenation
concatenation
	: additiveExpression ( CONCAT^ additiveExpression )*
	;

// level 3 - binary plus and minus
additiveExpression
	: multiplyExpression ( ( PLUS^ | MINUS^ | BIT_AND^ ) multiplyExpression )*
	;

// level 2 - binary multiply and divide
multiplyExpression
	: unaryExpression ( ( STAR^ | DIV^ ) unaryExpression )*
	;
	
// level 1 - unary minus, unary plus, not
unaryExpression
	: (m=MINUS^ {$m.tree.getToken().setType(UNARY_MINUS);}) unaryExpression
	| (p=PLUS^ {$p.tree.getToken().setType(UNARY_PLUS);}) unaryExpression
	| caseExpression                                                           
	| quantifiedExpression
	| atom
	;
	
caseExpression
	: CASE^ (whenClause)+ (elseClause)? END! 
	;
	
whenClause
	: (WHEN^ logicalExpression THEN! unaryExpression)
	;
	
altWhenClause
	: (WHEN^ unaryExpression THEN! unaryExpression)
	;
	
elseClause
	: (ELSE^ unaryExpression)
	;
	

quantifiedExpression
	: ( SOME^ | EXISTS^ | ALL^ | ANY^ ) 
	( identifier | (OPEN! ( subQuery ) CLOSE!) )
	;


// level 0 - expression atom
// ident qualifier ('.' ident ), array index ( [ expr ] ),
atom
    : primaryExpression ( DOT^ (identifier | starAtom) )*
	;

fragment
starAtom
    : STAR -> ROW_STAR
    ;

// level 0 - the basic element of an expression
primaryExpression
	:   identPrimary
	|   constant
	|   OPEN! ( subQuery | expression ) CLOSE!
	|   PARAM^ (NUM_INT)?
	;


// identifier, followed by member refs (dot ident), or method calls.
// NOTE: handleDotIdent() is called immediately after the first IDENT is recognized because
// the method looks a head to find keywords after DOT and turns them into identifiers.
identPrimary
	: identifier { handleDotIdent(); }
        ( options { greedy=true; } : (DOT^ identifier) )*
        ( options { greedy=true; } : op=OPEN^ {$op.tree.getToken().setType(METHOD_CALL);} exprList CLOSE! )?
	| aggregate
	| cast
	;


aggregate
    @after {$aggregate.tree.getToken().setType(AGGREGATE);}
	: (( SUM^ | AVG^ | MAX^ | MIN^ | STDDEV^ ) OPEN! expr=(additiveExpression) CLOSE!)
	| (COUNT^ OPEN! d=DISTINCT? expr=(additiveExpression | starAtom) CLOSE!)
	| (GROUP_CONCAT^ OPEN! d=DISTINCT? expr=(additiveExpression) CLOSE!)
	;


cast
    : (c=CAST open=OPEN expression as=AS identifier CLOSE)
        -> ^(METHOD_CALL IDENT[$c] ^(EXPR_LIST expression identifier))
	;


compoundExpr
	: path
	| OPEN! ( subQuery| expression (COMMA! expression)* ) CLOSE!
	;


subQuery
	: union
	;

exprList
	: exprListFragment -> ^(EXPR_LIST exprListFragment?)
 	;

fragment
exprListFragment
    : (expression (COMMA! expression)*)?
    ; 

constant
	: number
	| QUOTED_STRING
	| NULL
	| TRUE
	| FALSE
	| EMPTY
	;

number
    : NUM_INT
    | NUM_LONG
    | NUM_DOUBLE
    | NUM_FLOAT
    ;

path
	: identifier ( DOT^ { weakKeywords(); } identifier )*
	;

identifier
	: IDENT | QUOTED_IDENTIFIER
	;



// **** LEXER ******************************************************************

/**
 * Hibernate Query Language Lexer
 * <br>
 * This lexer provides the HQL parser with tokens.
 * @author Joshua Davis (pgmjsd@sourceforge.net)
 */


EQ: '=';
LT: '<';
GT: '>';
SQL_NE: '<>';
NE: '!=';
LE: '<=';
GE: '>=';

COMMA: ',';

OPEN: '(';
CLOSE: ')';

CONCAT: '||';
PLUS: '+';
MINUS: '-';
STAR: '*';
DIV: '/';
COLON: ':';
PARAM: '?';
BIT_OR: '|';
BIT_XOR: '^';
BIT_AND: '&';

IDENT
	: ID_START_LETTER ( ID_LETTER )*
		{
    		// Setting this flag allows the grammar to use keywords as identifiers, if necessary.
			setPossibleID(true);
		}
	;

fragment
ID_START_LETTER
    :    '_'
    |    '$'
    |    'a'..'z'
    |    'A'..'Z'
    |    '\u0080'..'\ufffe'       // HHH-558 : Allow unicode chars in identifiers
    ;


fragment
ID_LETTER
    :    ID_START_LETTER
    |    '0'..'9'
    ;


QUOTED_STRING
	: '\'' (~'\'' | '\'\'')* '\''
	;


QUOTED_IDENTIFIER 
	: '"' (~'"' | '""')* '"'
	;


fragment
WS 
	: (' ' | '\t' | '\r' | '\n')+ {skip();}
	;

//--- From the Java example grammar ---

fragment NUM_LONG : ;
fragment NUM_DOUBLE : ;
fragment NUM_FLOAT : ;

// a numeric literal
NUM_INT
	@init {boolean isDecimal=false; Token t=null;}
	:   '.' {_type = DOT;}
			(	('0'..'9')+ (EXPONENT)? (f1=FLOAT_SUFFIX {t=f1;})?
				{
					if (t != null && t.getText().toUpperCase().indexOf('F')>=0)
					{
						_type = NUM_FLOAT;
					}
					else
					{
						_type = NUM_DOUBLE; // assume double
					}
				}
			)?
	|	(	'0' {isDecimal = true;} // special case for just '0'
			(	('x')
				(											// hex
				:	HEX_DIGIT
				)+
			|	('0'..'7')+									// octal
			)?
		|	('1'..'9') ('0'..'9')*  {isDecimal=true;}		// non-zero decimal
		)
		(	('l') { _type = NUM_LONG; }

		// only check to see if it's a float if looks like decimal so far
		|	{isDecimal}?
			(   '.' ('0'..'9')* (EXPONENT)? (f2=FLOAT_SUFFIX {t=f2;})?
			|   EXPONENT (f3=FLOAT_SUFFIX {t=f3;})?
			|   f4=FLOAT_SUFFIX {t=f4;}
			)
			{
				if (t != null && t.getText().toUpperCase() .indexOf('F') >= 0)
				{
					_type = NUM_FLOAT;
				}
				else
				{
					_type = NUM_DOUBLE; // assume double
				}
			}
		)?
	;

// hexadecimal digit (again, note it's protected!)
fragment
HEX_DIGIT
	:	('0'..'9'|'a'..'f'|'A'..'F')
	;

// a couple protected methods to assist in matching floating point numbers
fragment
EXPONENT
	:	('e') ('+'|'-')? ('0'..'9')+
	;

fragment
FLOAT_SUFFIX
	:	'f'|'d'
	;

COMMENT
    :   '/*' (options {greedy=false;} : . )* '*/' {skip();}
    ;
    
LINE_COMMENT
    : '--' (~('\r'|'\n'))* ('\r\n' | '\n' | '\r')? {skip();}
    ;
