import re
with open("src/client/java/chiseled_enchanting_table/gui/EnchantementListWidget.java", "r") as f:
    code = f.read()

method = """
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            int index = parent.children().indexOf(this);
            int top = parent.getRowTop(index);
            int bottom = parent.getRowBottom(index);
            int left = parent.getRowLeft();
            int right = parent.getRowRight();
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }

        @Override
        public void extractContent"""

code = code.replace("        @Override\n        public void extractContent", method)

with open("src/client/java/chiseled_enchanting_table/gui/EnchantementListWidget.java", "w") as f:
    f.write(code)
