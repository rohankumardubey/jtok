package tok;

import java.util.List;

import static tok.TokenType.*;

public class Parser {
    /*
     * Our parser parses Tok using the following grammar:
     * expression   ->    equality ;
     * equality     ->    comparison ( ( "!=" | "==" ) comparison )* ;
     * comparison   ->    term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
     * term         ->    factor ( ( "-" | "+" ) factor )* ;
     * factor       ->    unary ( ( "/" | "*" ) unary )* ;
     * unary        ->    ( "!" | "-" ) unary
     *                    | primary ;
     * primary      ->    NUMBER | STRING | "true" | "false" | "nil"
     *                    | "(" expression ")" ;
     */

    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;

    // current points at the next token ready to be consumed.
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Expr expression() {
        return equality();
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
        //            | "(" expression ")" ;

        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
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