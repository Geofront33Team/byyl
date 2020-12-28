package sample;


import java.io.IOException;

public class SymbolTable {

//当前名字表项指针(有效的符号表大小)table size
	public int tablePtr = 0;
	// 符号表的大小
	
	public static final int tableMax = 100;
	public static final int symMax = 10; 	// 符号的最大长度
	public static final int addrMax = 1000000; // 最大允许的数值
	public static final int levMax = 3; 	// 最大允许过程嵌套声明层数[0,levmax]
	public static final int numMax = 14; 	// number的最大位数
	public static boolean tableswitch;		// 是否允许输出符号表
	// 名字表
	public Item[] table = new Item[tableMax];

	public class Item {

		public static final int constant = 0;
		public static final int variable = 1;
		public static final int procedure = 2;
		String name; 
		int type; 
		int value; 	// const
		int lev; 	// 所处层，var和procedure使用
		int addr; 	// 地址，var和procedure使用
		int size; 	// 需要分配的数据区空间，仅procedure使用

		public Item() {
			super();
			this.name = "";
		}

	}

//获得名字表某一项的内容
	public Item get(int i) {
		if (table[i] == null) {
			table[i] = new Item();
		}
		return table[i];
	}

//把某个符号登录到名字表中 名字表从1开始填，0表示不存在该项符号
	public void enter(Symbol sym, int type, int lev, int dataSize) {
		tablePtr++;
		Item item = get(tablePtr);
		item.name = sym.id;
		item.type = type;
		switch (type) {
		case Item.constant: 		// 常量名字
			item.value = sym.num; 	// 记录下常数值的大小
			break;
		case Item.variable: // 变量名字
			item.lev = lev; // 变量所在的层
			item.addr = dataSize; // 变量的偏移地址
			break;
		case Item.procedure: // 过程名字
			item.lev = lev;

		}
	}

//在名字表中查找某个名字的位置
	public int position(String idt) {
		for (int i = tablePtr; i > 0; i--)
		{
			if (get(i).name.equals(idt)) {
				return i;
			}
		}
		return 0;
	}

//输出符号表内容
	void debugTable(int start) {
		if (tableswitch) // 是否允许输出符号表
		{
			return;
		}
		System.out.println("**** Symbol Table ****");
		if (start > tablePtr) {
			System.out.println("  NULL");
		}
		for (int i = start + 1; i <= tablePtr; i++) {
			try {
				String msg = "unknown table item !";
				switch (table[i].type) {
				case Item.constant:
					msg = "   " + i + "  const: " + table[i].name + "  val: " + table[i].value;
					break;
				case Item.variable:
					msg = "    " + i + "  var: " + table[i].name + "  lev: " + table[i].lev + "  addr: "
							+ table[i].addr;
					break;
				case Item.procedure:
					msg = "    " + i + " proc: " + table[i].name + "  lev: " + table[i].lev + "  addr: "
							+ table[i].size;
					break;
				}
				System.out.println(msg);
//				PL0.tableWriter.write(msg + '\n');
			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println("***write table intfo meet with error***");
			}
		}
	}
}
