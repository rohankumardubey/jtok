package tok;

import java.util.ArrayList;
import java.util.List;

import static tok.TokenType.*;

public class Parser {
    /*
     * Our parser parses Tok using the following grammar:
     * program      ->    statement* EOF ;
     * declaration  ->    varDecl
                          | statement ;
     * varDecl      ->    "var" IDENTIFIER ( "=" expression )? ";" ;
     * statement    ->    exprStmt
     *                    | ifStmt
     *                    | printStmt
     *                    | block ;
     * ifStmt       ->    "if" "(" expression ")" statement
                          ( "else" statement )? ;
     * block        ->    "{" declaration* "}" ;
     * exprStmt     ->    expression ";" ;
     * printStmt    ->    "print" expression ";" ;
     * expression   ->    assignment ;
     * assignment   ->    IDENTIFIER "=" assignment
                          | equality ;
     * equality     ->    comparison ( ( "!=" | "==" ) comparison )* ;
     * comparison   ->    term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
     * term         ->    factor ( ( "-" | "+" ) factor )* ;
     * factor       ->    unary ( ( "/" | "*" ) unary )* ;
     * unary        ->    ( "!" | "-" ) unary
     *                    | primary ;
     * primary      ->    NUMBER | STRING | "true" | "false" | "nil"
     *                    | "(" expression ")"
     *                    | IDENTIFIER ;
     */

    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;

    // current points at the next token ready to be consumed.
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            // This gets it back to trying to parse the beginning of the next statement or declaration.
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        // * statement    ->    exprStmt
        //                      | printStmt ;

        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'if' condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        // printStmt    ->    "print" expression ";" ;

        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr assignment() {
        // assignment   ->    IDENTIFIER "=" assignment
        //                    | equality ;
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {

                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        // equality -> comparison ( ( "!=" | "==" ) comparison )* ;

        // the first comparison non terminal in the equality rule translates to the comparison() call below.
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {

            // we grab the matched operator token.
            Token operator = previous();

            // we call comparison() again to parse the right hand operand.
            Expr right = comparison();

            // finally, we combine the operator ad the two operands into a binary syntax tree node.
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;

        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        // term -> factor ( ( "-" | "+" ) factor )* ;

        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        // factor -> unary ( ( "/" | "*" ) unary )* ;

        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        // unary -> ( "!" | "-" ) unary
        //          | primary ;

        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        // primary -> NUMBER | STRING | "true" | "false" | "nil"
        //            | "(" expression ")" | IDENTIFIER ;

        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expected ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // If we don't get any matches, it means we are sitting on a token that can't start an expression.
        throw error(peek(), "Expected expression.");
    }


    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        // returns the current token that we are yet to consume.
        return tokens.get(current);
    }

    private Token previous() {
        // returns the most recently consumed token. This method makes it easier to use match and return the just-matched token
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        tok.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        // we enter panic mode and start discarding tokens until we get to a new statement/ point where tokens are sync'd.

        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }
}
