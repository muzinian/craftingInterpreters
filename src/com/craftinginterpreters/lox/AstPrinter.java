package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Grouping;
import com.craftinginterpreters.lox.Expr.Literal;
import com.craftinginterpreters.lox.Expr.Unary;

public class AstPrinter implements Expr.Visitor<String> {

    @Override
    public String visitBinaryExpr(Binary expr) {
        
        return parenthesize(expr.operator.getLexeme(), expr.left,expr.right);
    }

    @Override
    public String visitGroupingExpr(Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Literal expr) {
        if(expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Unary expr) {
        return parenthesize(expr.operator.getLexeme(),expr.right);
    }

    String print(Expr expr){
        return expr.accept(this);
    }

    private String parenthesize(String name,Expr... exprs){
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(name);
        for(Expr expr:exprs){
            sb.append( " ");
            sb.append(expr.accept(this));
        }
        sb.append(")");
        return sb.toString();
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
            new Expr.Unary(
                new Token(TokenType.MINUS, "-", null, 1),
                new Expr.Literal(123)),
            new Token(TokenType.STAR, "*", null, 1),
            new Expr.Grouping(
                new Expr.Literal(45.67)));
    
        System.out.println(new AstPrinter().print(expression));
      }
}
