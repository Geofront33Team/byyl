package sample;

import java.io.IOException;
import java.util.BitSet;

public class Praser {
	private Symbol sym; 
	private Scanner lex; 
	public SymbolTable table; 
	public Err myErr;

	private BitSet declFirst;//声明的FIRST集合
	private BitSet statFirst;//语句的FIRST集合
	private BitSet facFirst;//因子的FIRST集合
	private int dataSize;//计算每个变量在运行栈中相对本过程基地址的偏移量,symbolTable中的address

	public Praser(Scanner lex, SymbolTable table) {
		this.lex = lex;
		this.table = table;
		this.myErr = new Err();
		this.dataSize = 0;

		//BitSet创建一个位集，其初始大小足够大，可以显式地表示索引在0到nbits-1范围内的位。所有位最初都为假
		//BitSet.set 将指定索引处的位设置为 true
		// declare
		declFirst = new BitSet(Symbol.symnum);
		declFirst.set(Symbol.constsym); 
		declFirst.set(Symbol.varsym);
		declFirst.set(Symbol.procsym);

		// statement
		statFirst = new BitSet(Symbol.symnum);
		statFirst.set(Symbol.beginsym);
		statFirst.set(Symbol.callsym);
		statFirst.set(Symbol.ifsym);
		statFirst.set(Symbol.whilesym);
		statFirst.set(Symbol.repeatsym);

		// factor
		facFirst = new BitSet(Symbol.symnum);
		facFirst.set(Symbol.ident);
		facFirst.set(Symbol.number);
		facFirst.set(Symbol.lparen);
	}

	public void nextsym() {
		sym = lex.getsym();
	}

	//当前单词是否合法
	void test(BitSet s1, BitSet s2, int errcode) {

		if (!s1.get(sym.symtype)) {
			myErr.report(errcode, lex.lineCnt);

			s1.or(s2);
			while (!s1.get(sym.symtype)) {
				nextsym();
			}
		}
	}

	//分程序分析处理
	public void block(int lev, BitSet followSys) {
		BitSet nxtlev = new BitSet(Symbol.symnum);

		int dataSize0 = dataSize, // 记录本层之前的数据量,以便返回时恢复
				tx0 = table.tablePtr, // 记录本层名字的初始位置
				dataSize = 3;////每一层最开始的位置有三个空间用于存放静态链SL、动态链DL和返回地址RA



		if (lev > SymbolTable.levMax) // 判断嵌套层层数
		{
			myErr.report(32, lex.lineCnt); // 嵌套层数过大
		}

		// <说明部分>
		do {
			//<常量说明部分> ::= const<常量定义>{,<常量定义>};
			if (sym.symtype == Symbol.constsym) {
				nextsym();
				constdeclaration(lev); // 返回所在层数
				while (sym.symtype == Symbol.comma) {
					//逗号
					nextsym();
					constdeclaration(lev);
				}

				if (sym.symtype == Symbol.semicolon) {
					//分号,结束
					nextsym();
				} else {
					myErr.report(5, lex.lineCnt); // 漏了逗号或者分号
				}
			}

			// <变量说明部分>::=var<标识符>{,<标识符>};
			if (sym.symtype == Symbol.varsym) {
				nextsym();
				vardeclaration(lev); 
				while (sym.symtype == Symbol.comma) { 
					//逗号
					nextsym();
					vardeclaration(lev);
				}
				if (sym.symtype == Symbol.semicolon) {
					//分号，结束
					nextsym();
				} else {
					myErr.report(5, lex.lineCnt); // 漏了逗号或者分号
				}
			}

			//<过程说明部分> ::=  procedure<标识符>; <分程序> ;
			while (sym.symtype == Symbol.procsym) {
				nextsym();
				if (sym.symtype == Symbol.ident) {
					table.enter(sym, SymbolTable.Item.procedure, lev, dataSize); // 当前作用域的大小
					nextsym();
				} else {
					myErr.report(4, lex.lineCnt); // procedure后应为标识符
				}

				if (sym.symtype == Symbol.semicolon) {
					nextsym();
				} else {
					myErr.report(5, lex.lineCnt); // 漏了逗号或者分号
				}

				nxtlev = (BitSet) followSys.clone(); 
				// 当前模块(block)的FOLLOW集合
				//FOLLOW(block)={ ; }
				nxtlev.set(Symbol.semicolon);
				block(lev + 1, nxtlev); // 嵌套层次+1，分析分程序

				// <过程说明部分> 识别成功
				if (sym.symtype == Symbol.semicolon) {

					nextsym();
					nxtlev = (BitSet) statFirst.clone();////FIRST(statement)={begin call if while repeat null };
					nxtlev.set(Symbol.ident);
					nxtlev.set(Symbol.procsym);
					test(nxtlev, followSys, 6);
					// 测试symtype属于FIRST(statement),
					// 6:过程说明后的符号不正确
				} else {
					myErr.report(5, lex.lineCnt); // 漏了逗号或者分号
				}
			}

			// statement
			// procedure
			nxtlev = (BitSet) statFirst.clone();
			nxtlev.set(Symbol.ident);
			test(nxtlev, declFirst, 7);
		} while (declFirst.get(sym.symtype));

		// 开始生成当前过程代码
		SymbolTable.Item item = table.get(tx0);

		item.size = dataSize;


		// 打印<说明部分>代码
		table.debugTable(tx0);

		// <语句>
		nxtlev = (BitSet) followSys.clone();
		nxtlev.set(Symbol.semicolon);
		nxtlev.set(Symbol.endsym);
		statement(nxtlev, lev);


		nxtlev = new BitSet(Symbol.symnum);
		test(followSys, nxtlev, 8);
		//8.程序体内语句后的符号不正确


		dataSize = dataSize0;
		table.tablePtr = tx0;
	}

	public void parse() {
		BitSet nxtlev = new BitSet(Symbol.symnum);
		nxtlev.or(declFirst);
		nxtlev.or(statFirst);
		nxtlev.set(Symbol.peroid);

		block(0, nxtlev);

		if (sym.symtype != Symbol.peroid) // 缺少句号
		{
			myErr.report(9, lex.lineCnt);
		}

		table.debugTable(0); // 输出符号表
	}

//<常量定义> <常量定义> ::= <标识符>=<无符号整数>
	void constdeclaration(int lev) {
		if (sym.symtype == Symbol.ident) {
			String id = sym.id;
			nextsym();
			if (sym.symtype == Symbol.eql || sym.symtype == Symbol.becomes) {
				if (sym.symtype == Symbol.becomes) {
					myErr.report(1, lex.lineCnt); 
					// 把=写成了：=
				}
				nextsym(); // 自动纠错使编译继续进行：赋值号当作等号处理
				if (sym.symtype == Symbol.number) {
					sym.id = id;
					table.enter(sym, SymbolTable.Item.constant, lev, dataSize);
					nextsym();
				} else {
					myErr.report(2, lex.lineCnt); // 常量说明=后应是数字
				}
			} else {
				myErr.report(3, lex.lineCnt); // 常量说明标志后应是=
			}
		} else {
			myErr.report(4, lex.lineCnt); // const后应是标识符
		}
	}

//<标识符> <变量说明部分>::= var <标识符> { , <标识符> } ;
	void vardeclaration(int lev) {
		if (sym.symtype == Symbol.ident) {
			/**
			 * 填写名字表并改变堆栈帧计数器 符号表中记录下标识符的名字、它所在的层及它在所在层中的偏移地址
			 */
			table.enter(sym, SymbolTable.Item.variable, lev, dataSize);
			/**
			 * 变量定义过程中,会用dataSize变量记录下局部数据段分配的空间个数
			 */
			dataSize++;
			nextsym();
		} else {
			myErr.report(4, lex.lineCnt); // var后应是标识符
		}
	}

//<语句>
	void statement(BitSet followSys, int lev) {
		// FIRST(statement)={ident,read,write,call,if, while}
		switch (sym.symtype) {
		case Symbol.ident:
			praseAssignStatement(followSys, lev);
			break;
		case Symbol.readsym:
			praseReadStatement(followSys, lev);
			break;
		case Symbol.writesym:
			praseWriteStatement(followSys, lev);
			break;
		case Symbol.callsym:
			praseCallStatement(followSys, lev);
			break;
		case Symbol.ifsym:
			praseIfStatement(followSys, lev);
			break;
		case Symbol.beginsym:
			praseBeginStatement(followSys, lev);
			break;
		case Symbol.whilesym:
			praseWhileStatement(followSys, lev);
			break;
		case Symbol.repeatsym:
			praseRepeatStatement(followSys, lev);
			break;
		default:
			BitSet nxlev = new BitSet(Symbol.symnum);
			test(followSys, nxlev, 19); // 语句后的符号不正确
			break;
		}
	}

//<重复语句> ::= repeat<语句>{;<语句>}until<条件>
	private void praseRepeatStatement(BitSet followSys, int lev) {
		nextsym();
		BitSet nxtlev = (BitSet) followSys.clone();
		nxtlev.set(Symbol.semicolon);
		nxtlev.set(Symbol.untilsym);
		statement(followSys, lev);

		while (statFirst.get(sym.symtype) || sym.symtype == Symbol.semicolon) {
			if (sym.symtype == Symbol.semicolon) {
				nextsym();
			} else {
				myErr.report(34, lex.lineCnt);
			}

			statement(nxtlev, lev);
		}
		if (sym.symtype == Symbol.untilsym) {
			nextsym();
			condition(followSys, lev);
		} else {
			myErr.report(dataSize);
		}
	}

//<当型循环语句> <当型循环语句> ::= while<条件>do<语句> 
	private void praseWhileStatement(BitSet followSys, int lev) {
		nextsym();
		BitSet nxtlev = (BitSet) followSys.clone();
		nxtlev.set(Symbol.dosym);
		condition(nxtlev, lev);

		if (sym.symtype == Symbol.dosym) {
			nextsym();
		} else {
			myErr.report(18, lex.lineCnt); // 缺少do
		}
		statement(followSys, lev);
	}

//<复合语句> <复合语句> ::= begin<语句>{;<语句>}end
	private void praseBeginStatement(BitSet followSys, int lev) {
		nextsym();
		BitSet nxtlev = (BitSet) followSys.clone();
		nxtlev.set(Symbol.semicolon);
		nxtlev.set(Symbol.endsym);
		statement(nxtlev, lev);

		while (statFirst.get(sym.symtype) || sym.symtype == Symbol.semicolon) {
			if (sym.symtype == Symbol.semicolon) {
				nextsym();
			} else {
				myErr.report(10, lex.lineCnt); // 缺少分号
			}
			statement(nxtlev, lev);
		}
		if (sym.symtype == Symbol.endsym) // 若为end ，statement解析成功
		{
			nextsym();
		} else {
			myErr.report(17, lex.lineCnt); // 缺少end 或者分号
		}
	}

//<条件语句> <条件语句> ::= if <条件> then <语句>
	private void praseIfStatement(BitSet followSys, int lev) {
		nextsym();
		BitSet nxtlev = (BitSet) followSys.clone();
		nxtlev.set(Symbol.thensym);
		nxtlev.set(Symbol.dosym);
		condition(nxtlev, lev); // 分析<条件>
		if (sym.symtype == Symbol.thensym) {
			nextsym();
		} else {
			myErr.report(16, lex.lineCnt); // 缺少then
		}
	
		statement(followSys, lev);

		if (sym.symtype == Symbol.elsesym) {
			nextsym();
			statement(followSys, lev);
		}

	}

//<标识符> <过程调用语句> ::= call<标识符> 
	private void praseCallStatement(BitSet followSys, int lev) {
		nextsym();
		if (sym.symtype == Symbol.ident) {
			int index = table.position(sym.id);
			if (index != 0) {
				SymbolTable.Item item = table.get(index);
				if (item.type == SymbolTable.Item.procedure) {
				} else {
					myErr.report(15, lex.lineCnt); // call后标识符应为过程
				}
			} else {
				myErr.report(11, lex.lineCnt); // 过程调用未找到
			}
			nextsym();
		} else {
			myErr.report(14, lex.lineCnt); // call后应为标识符
		}
	}

//'(' <表达式> { , <表达式> } ')' <写语句> ::= write '(' <表达式> { , <表达式> } ')'
	private void praseWriteStatement(BitSet followSys, int lev) {
		nextsym();
		if (sym.symtype == Symbol.lparen) {
			do {
				nextsym();
				BitSet nxtlev = (BitSet) followSys.clone();
				// FOLLOW={ , ')' }
				nxtlev.set(Symbol.rparen);
				nxtlev.set(Symbol.comma);
				expression(nxtlev, lev);
			} while (sym.symtype == Symbol.comma);

			if (sym.symtype == Symbol.rparen) // 解析成功
			{
				nextsym();
			} else {
				myErr.report(33, lex.lineCnt); // 格式错误，应为右括号
			}
		} else {
			myErr.report(34, lex.lineCnt); // 格式错误，应为右括号
		}
	}

//'(' <标识符> { , <标识符> } ')' <读语句> ::= read '(' <标识符> { , <标识符> } ')'
	private void praseReadStatement(BitSet followSys, int lev) {
		nextsym();
		if (sym.symtype == Symbol.lparen) {
			int index = 0;
			do {
				nextsym();
				if (sym.symtype == Symbol.ident) {
					index = table.position(sym.id);
				}
				if (index == 0) {
					myErr.report(35, lex.lineCnt); // read()中应是声明过的变量名
				} else {
					SymbolTable.Item item = table.get(index);
					if (item.type != SymbolTable.Item.variable) {
						myErr.report(32, lex.lineCnt); // read()中的标识符不是变量
					} else {
		
					}
				}
				nextsym();
			} while (sym.symtype == Symbol.comma);
		} else {
			myErr.report(34, lex.lineCnt); // 格式错误，应是左括号
		}

		if (sym.symtype == Symbol.rparen) // 匹配成功
		{
			nextsym();
		} else {
			myErr.report(33, lex.lineCnt); // 格式错误，应是右括号
			while (!followSys.get(sym.symtype)) {
				nextsym();
			}
		}
	}

//:=<表达式> <赋值语句> ::= <标识符>:=<表达式>
	private void praseAssignStatement(BitSet followSys, int lev) {

		int index = table.position(sym.id);
		if (index > 0) {
			SymbolTable.Item item = table.get(index);
			if (item.type == SymbolTable.Item.variable) {
				nextsym();
				if (sym.symtype == Symbol.becomes) {
					nextsym();
				} else {
					myErr.report(13, lex.lineCnt); // 没有检测到赋值符号
				}
				BitSet nxtlev = (BitSet) followSys.clone();
				expression(nxtlev, lev);
			} else {
				myErr.report(12, lex.lineCnt); // 不可向常量或过程名赋值
			}
		} else {
			myErr.report(11, lex.lineCnt); // 标识符未说明
		}
	}

//<表达式> <表达式> ::= [+|-]<项>{<加法运算符><项>}
	private void expression(BitSet followSys, int lev) {
		if (sym.symtype == Symbol.plus || sym.symtype == Symbol.minus) { // 分析[+|-]<项>
			int addOperatorType = sym.symtype;
			nextsym();
			BitSet nxtlev = (BitSet) followSys.clone();
			nxtlev.set(Symbol.plus);
			nxtlev.set(Symbol.minus);
			term(nxtlev, lev);
			if (addOperatorType == Symbol.minus) {

			}

		} else {
			BitSet nxtlev = (BitSet) followSys.clone();
			nxtlev.set(Symbol.plus);
			nxtlev.set(Symbol.minus);
			term(nxtlev, lev);
		}

		while (sym.symtype == Symbol.plus || sym.symtype == Symbol.minus) {
			int addOperatorType = sym.symtype;
			nextsym();
			BitSet nxtlev = (BitSet) followSys.clone();
			// FOLLOW(term)={ +,- }
			nxtlev.set(Symbol.plus);
			nxtlev.set(Symbol.minus);
			term(nxtlev, lev);
		}
	}

//<项> <项> ::= <因子>{<乘法运算符><因子>} 除法
	private void term(BitSet followSys, int lev) {

		BitSet nxtlev = (BitSet) followSys.clone();
		nxtlev.set(Symbol.mul);
		nxtlev.set(Symbol.div);
		factor(nxtlev, lev);
		while (sym.symtype == Symbol.mul || sym.symtype == Symbol.div) {
			int mulOperatorType = sym.symtype;
			nextsym();
			factor(nxtlev, lev);
		}
	}

//<因子> <因子>=<标识符>|<无符号整数>|'('<表达式>')'
	private void factor(BitSet followSys, int lev) {
		test(facFirst, followSys, 24);
		if (facFirst.get(sym.symtype)) {
			if (sym.symtype == Symbol.ident) {
				// 因子为常量或变量或者过程名
				int index = table.position(sym.id);
				if (index > 0) {
					SymbolTable.Item item = table.get(index);
					switch (item.type) {

					case SymbolTable.Item.constant:
						break;
					case SymbolTable.Item.variable:

						break;
					case SymbolTable.Item.procedure:
						myErr.report(21, lex.lineCnt);
						break;
					}
				} else {
					myErr.report(11, lex.lineCnt); // 标识符未声明
				}
				nextsym();
			} else if (sym.symtype == Symbol.number) {
				// 因子为数
				int num = sym.num;
				if (num > SymbolTable.addrMax) {
					myErr.report(31, lex.lineCnt);// 数越界
					num = 0;
				}
				nextsym();
			} else if (sym.symtype == Symbol.lparen) {
				// 因子为表达式：'('<表达式>')'
				nextsym();
				BitSet nxtlev = (BitSet) followSys.clone();
				// FOLLOW(expression)={ ) }
				nxtlev.set(Symbol.rparen);
				expression(nxtlev, lev);
				if (sym.symtype == Symbol.rparen) // 匹配成功
				{
					nextsym();
				} else {
					myErr.report(22, lex.lineCnt); // 缺少右括号
				}
			} else {
				test(followSys, facFirst, 23);
				// 一个因子处理完毕，遇到的token应在followSys集合中
				// 如果不是，抛23号错，并找到下一个因子的开始，使语法分析可以继续运行下去
			}
		}
	}

//<条件> <表达式><关系运算符><表达式>|odd<表达式> 
	private void condition(BitSet followSys, int lev) {
		if (sym.symtype == Symbol.oddsym) {
			nextsym();
			expression(followSys, lev);
		} else {
			BitSet nxtlev = (BitSet) followSys.clone();

			nxtlev.set(Symbol.eql);
			nxtlev.set(Symbol.neq);
			nxtlev.set(Symbol.lss);
			nxtlev.set(Symbol.leq);
			nxtlev.set(Symbol.gtr);
			nxtlev.set(Symbol.geq);
			expression(nxtlev, lev);
			if (sym.symtype == Symbol.eql || sym.symtype == Symbol.neq || sym.symtype == Symbol.lss
					|| sym.symtype == Symbol.leq || sym.symtype == Symbol.gtr || sym.symtype == Symbol.geq) {
				int relationOperatorType = sym.symtype;
				nextsym();
				expression(followSys, lev);
			} else {
				myErr.report(20, lex.lineCnt); // 应为关系运算符
			}
		}
	}

	void debug(String msg) {
		System.out.println("*** DEDUG : " + msg + "  ***");
	}
}
