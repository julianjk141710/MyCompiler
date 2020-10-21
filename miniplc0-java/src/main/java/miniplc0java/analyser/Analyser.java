package miniplc0java.analyser;

import miniplc0java.error.AnalyzeError;
import miniplc0java.error.CompileError;
import miniplc0java.error.ErrorCode;
import miniplc0java.error.ExpectedTokenError;
import miniplc0java.error.TokenizeError;
import miniplc0java.instruction.Instruction;
import miniplc0java.instruction.Operation;
import miniplc0java.tokenizer.Token;
import miniplc0java.tokenizer.TokenType;
import miniplc0java.tokenizer.Tokenizer;
import miniplc0java.util.Pos;

import java.util.*;

import static miniplc0java.error.ErrorCode.DuplicateDeclaration;

public final class Analyser {

    Tokenizer tokenizer;
    ArrayList<Instruction> instructions;

    /** 当前偷看的 token */
    Token peekedToken = null;

    /** 符号表 */
    HashMap<String, SymbolEntry> symbolTable = new HashMap<>();

    /** 下一个变量的栈偏移 */
    int nextOffset = 0;

//    int PrintFlag = 0;

    public Analyser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.instructions = new ArrayList<>();
    }

    public List<Instruction> analyse() throws CompileError {
        analyseProgram();
        return instructions;
    }

    /**
     * 查看下一个 Token
     * 
     * @return
     * @throws TokenizeError
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }

    /**
     * 获取下一个 Token
     * 
     * @return
     * @throws TokenizeError
     */
    private Token next() throws TokenizeError {
        if (peekedToken != null) {
            var token = peekedToken;
            peekedToken = null;
            return token;
        } else {
            return tokenizer.nextToken();
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     * 
     * @param tt
     * @return
     * @throws TokenizeError
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回这个 token
     * 
     * @param tt 类型
     * @return 如果匹配则返回这个 token，否则返回 null
     * @throws TokenizeError
     */
    private Token nextIf(TokenType tt) throws TokenizeError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            return null;
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回，否则抛出异常
     * 
     * @param tt 类型
     * @return 这个 token
     * @throws CompileError 如果类型不匹配
     */
    private Token expect(TokenType tt) throws CompileError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            throw new ExpectedTokenError(tt, token);
        }
    }

    /**
     * 获取下一个变量的栈偏移
     * 
     * @return
     */
    private int getNextVariableOffset() {
        return this.nextOffset++;
    }

    /**
     * 添加一个符号
     * 
     * @param name          名字
     * @param isInitialized 是否已赋值
     * @param isConstant    是否是常量
     * @param curPos        当前 token 的位置（报错用）
     * @throws AnalyzeError 如果重复定义了则抛异常
     */
    private void addSymbol(String name, boolean isInitialized, boolean isConstant, Pos curPos) throws AnalyzeError {
        if (this.symbolTable.get(name) != null) {
            throw new AnalyzeError(DuplicateDeclaration, curPos);
        } else {
            this.symbolTable.put(name, new SymbolEntry(isConstant, isInitialized, getNextVariableOffset()));
        }
    }

    /**
     * 设置符号为已赋值
     * 
     * @param name   符号名称
     * @param curPos 当前位置（报错用）
     * @throws AnalyzeError 如果未定义则抛异常
     */
    private void declareSymbol(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            entry.setInitialized(true);
        }
    }

    /**
     * 获取变量在栈上的偏移
     * 
     * @param name   符号名
     * @param curPos 当前位置（报错用）
     * @return 栈偏移
     * @throws AnalyzeError
     */
    private int getOffset(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.getStackOffset();
        }
    }

    /**
     * 获取变量是否是常量
     * 
     * @param name   符号名
     * @param curPos 当前位置（报错用）
     * @return 是否为常量
     * @throws AnalyzeError
     */
    private boolean isConstant(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.isConstant();
        }
    }

    /**
     * <程序> ::= 'begin'<主过程>'end'
     */
    private void analyseProgram() throws CompileError {
        // 示例函数，示例如何调用子程序
        // 'begin'
        expect(TokenType.Begin);

        analyseMain();

        // 'end'
        expect(TokenType.End);
        expect(TokenType.EOF);
    }

    /**
     * <主过程> ::= <常量声明><变量声明><语句序列>
     * @throws CompileError
     */
    private void analyseMain() throws CompileError {
        //常量声明
        analyseConstantDeclaration();

        //变量声明
        analyseVariableDeclaration();

        //语句序列
        analyseStatementSequence();

        //throw new Error("Not implemented");
    }

    /**
     * 常量声明 实例函数
     * <常量声明> ::= {<常量声明语句>}  大括号表示重复
     * <常量声明语句> ::= 'const'<标识符>'='<常表达式>';'
     *
     * 注意循环语句的判断条件和实现的作用
     * @throws CompileError
     */
    private void analyseConstantDeclaration() throws CompileError {
        // 示例函数，示例如何解析常量声明
        // 如果下一个 token 是 const 就继续
        while (nextIf(TokenType.Const) != null) {
            // 变量名 (即文法中所说的标识符)
            var nameToken = expect(TokenType.Ident);

            //如果已经声明过这个常量
            if (symbolTable.containsKey(nameToken.getValue())) {
                throw new AnalyzeError(DuplicateDeclaration, nameToken.getStartPos());
            }
            // 等于号
            expect(TokenType.Equal);

            // 常表达式 并且返回这个整数值
            int valForStack = analyseConstantExpression();

            // 分号
            expect(TokenType.Semicolon);

            //添加符号到符号表内
            addSymbol(String.valueOf(nameToken.getValue()), true, true, nameToken.getStartPos());

            instructions.add(new Instruction(Operation.LIT, valForStack));
        }
    }

    /**
     * 变量声明
     * <变量声明> ::= {<变量声明语句>}
     * <变量声明语句> ::= 'var'<标识符>['='<表达式>]';'
     * @throws CompileError
     */
    private void analyseVariableDeclaration() throws CompileError {
        while (nextIf(TokenType.Var) != null) {
            // 变量名 (即文法中所说的标识符)
            var nameToken = expect(TokenType.Ident);
            //如果这是第二次声明该变量
            if (symbolTable.containsKey(nameToken.getValue())) {
                throw new AnalyzeError(DuplicateDeclaration, nameToken.getStartPos());
            }
            //处理可选项
            // ['='<表达式>]';'
            if (nextIf(TokenType.Equal) != null) {
                //表达式
                analyseExpression();
                //分号
                expect(TokenType.Semicolon);
                addSymbol(String.valueOf(nameToken.getValue()), true, false, nameToken.getStartPos());
                continue;
            }
            //分号
            expect(TokenType.Semicolon);

            addSymbol(String.valueOf(nameToken.getValue()), false, false, nameToken.getStartPos());



        }
        //throw new Error("Not implemented");
    }

    /**
     * 语句序列
     * <语句序列> ::= {<语句>}
     * @throws CompileError
     */
    private void analyseStatementSequence() throws CompileError {
        while (check(TokenType.Ident) || check(TokenType.Print) || check(TokenType.Semicolon)) {
            analyseStatement();
        }
        //throw new Error("Not implemented");
    }

    /**
     * 语句
     * <语句> ::= <赋值语句>|<输出语句>|<空语句>
     * @throws CompileError
     */
    private void analyseStatement() throws CompileError {
        if (check(TokenType.Ident)) {
            analyseAssignmentStatement();
        } else if (check(TokenType.Print)) {
            analyseOutputStatement();
        } else if (check(TokenType.Semicolon)) {
            next();
        } else {
            throw new AnalyzeError(ErrorCode.InvalidInput, next().getStartPos());
        }
        //throw new Error("Not implemented");
    }

    /**
     *常表达式
     * @throws CompileError
     */
    private int analyseConstantExpression() throws CompileError {
        int sign = 1;
        //可选项 符号
        if (nextIf(TokenType.Minus) != null) {
            sign = -1;
        } else if (nextIf(TokenType.Plus) != null){
            sign = 1;
        }

        //无符号整数
        var tokenVal = expect(TokenType.Uint);

        return sign * Integer.parseInt(String.valueOf(tokenVal.getValue()));
        //throw new Error("Not implemented");
    }

    /**
     * 表达式
     * <表达式> ::= <项>{<加法型运算符><项>}
     * @throws CompileError
     */
    private void analyseExpression() throws CompileError {
        //项
        analyseItem();

        while (check(TokenType.Minus) || check(TokenType.Plus)) {
            Token token = next();
            analyseItem();
            if (token.getTokenType() == TokenType.Minus) {
                instructions.add(new Instruction(Operation.SUB));
            } else if (token.getTokenType() == TokenType.Plus) {
                instructions.add(new Instruction(Operation.ADD));
            }
        }
        //throw new Error("Not implemented");
    }

    /**
     * 赋值语句
     * <赋值语句> ::= <标识符>'='<表达式>';'
     * @throws CompileError
     */
    private void analyseAssignmentStatement() throws CompileError {
        //标识符
        var nameToken = expect(TokenType.Ident);

        //如果没有这个变量 或者 这个变量是一个常量的话 抛异常
        if ( ! symbolTable.containsKey(nameToken.getValue())) {
            throw new AnalyzeError(ErrorCode.NotDeclared, nameToken.getStartPos());
        } else if (isConstant(String.valueOf(nameToken.getValue()), nameToken.getStartPos())) {
            throw new AnalyzeError(ErrorCode.AssignToConstant ,nameToken.getStartPos());
        }
        //等号
        expect(TokenType.Equal);

        //表达式
        analyseExpression();

        //分号
        expect(TokenType.Semicolon);

        if (symbolTable.get(nameToken.getValue()).isInitialized) {
            instructions.add(new Instruction(Operation.STO, getOffset((String)nameToken.getValue(), nameToken.getStartPos())));
        } else {
            declareSymbol(String.valueOf(nameToken.getValue()), nameToken.getStartPos());
        }
//        declareSymbol(String.valueOf(nameToken.getValue()), nameToken.getStartPos());
//        int offsetForStack = symbolTable.get(nameToken).stackOffset;
//        instructions.add(new Instruction(Operation.STO, offsetForStack));
        //throw new Error("Not implemented");
    }

    /**
     * 输出语句
     * <输出语句> ::= 'print' '(' <表达式> ')' ';'
     *
     * 并添加了一条指令
     * @throws CompileError
     */
    private void analyseOutputStatement() throws CompileError {
        expect(TokenType.Print);
        expect(TokenType.LParen);
        //PrintFlag = 1;
        analyseExpression();
        //PrintFlag = 0;
        expect(TokenType.RParen);
        expect(TokenType.Semicolon);
        instructions.add(new Instruction(Operation.WRT));
    }

    /**
     * 项
     * <项> ::= <因子>{<乘法型运算符><因子>}
     * @throws CompileError
     */
    private void analyseItem() throws CompileError {
        analyseFactor();
        while (check(TokenType.Mult) || check(TokenType.Div)) {
            Token token = next();
            analyseFactor();
            if (token.getTokenType() == TokenType.Mult) {
                instructions.add(new Instruction(Operation.MUL));
            } else if (token.getTokenType() == TokenType.Div) {
                instructions.add(new Instruction(Operation.DIV));
            }
        }
        //throw new Error("Not implemented");
    }

    /**
     * 因子
     * <因子> ::= [<符号>]( <标识符> | <无符号整数> | '('<表达式>')' )
     * @throws CompileError
     */
    private void analyseFactor() throws CompileError {
        boolean negate;
        if (nextIf(TokenType.Minus) != null) {
            negate = true;
            // 计算结果需要被 0 减
            instructions.add(new Instruction(Operation.LIT, 0));
        } else {
            nextIf(TokenType.Plus);
            negate = false;
        }

        if (check(TokenType.Ident)) {
            // 调用相应的处理函数
            var nameToken = next();
            //如果该变量未声明过
            if (!symbolTable.containsKey(nameToken.getValue())) {
                throw new AnalyzeError(ErrorCode.NotDeclared, nameToken.getStartPos());
            } else if (!symbolTable.get(nameToken.getValue()).isInitialized()) {
                throw new AnalyzeError(ErrorCode.NotInitialized, nameToken.getStartPos());
            }
            int offsetForStack = getOffset((String)nameToken.getValue(), nameToken.getStartPos());
//            if (PrintFlag == 0) {
//                instructions.add(new Instruction(Operation.LOD, offsetForStack));
//            }
            instructions.add(new Instruction(Operation.LOD, offsetForStack));
            //int alpha = ;
            //INteruction.add(lit, alpha.value)
        } else if (check(TokenType.Uint)) {
            // 调用相应的处理函数
            var nameToken = next();
            int valForStack = (Integer) nameToken.getValue();
            instructions.add(new Instruction(Operation.LIT, valForStack));
            //int beta = xx;
        } else if (check(TokenType.LParen)) {
            // 调用相应的处理函数
            expect(TokenType.LParen);
            analyseExpression();
            expect(TokenType.RParen);
        } else {
            // 都不是，摸了
            throw new ExpectedTokenError(List.of(TokenType.Ident, TokenType.Uint, TokenType.LParen), next());
        }

        if (negate) {
            instructions.add(new Instruction(Operation.SUB));
        }
        //throw new Error("Not implemented");
    }
}
