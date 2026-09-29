package com.nexus.terminal.engine

import android.graphics.Color
import kotlin.math.max

class TerminalBuffer(private var cols:Int=100, private var rows:Int=32, private val scrollback:Int=5000){
    data class Cell(var ch:Char=' ', var fg:Int=Color.rgb(230,237,243), var bg:Int=Color.TRANSPARENT, var bold:Boolean=false)
    private val lines=ArrayDeque<MutableList<Cell>>()
    private var cx=0; private var cy=0; private var fg=Color.rgb(230,237,243); private var bg=Color.TRANSPARENT; private var bold=false
    private var esc=false; private var csi=false; private val params=StringBuilder()
    init{repeat(rows){lines.addLast(newLine())}}
    private fun newLine()=MutableList(cols){Cell()}
    fun resize(c:Int,r:Int){val nc=max(1,c);val nr=max(1,r);if(nc!=cols){lines.forEach{line->while(line.size<nc)line.add(Cell());while(line.size>nc)line.removeAt(line.lastIndex)}};cols=nc;rows=nr;while(lines.size<rows)lines.addLast(newLine());while(lines.size>scrollback)lines.removeFirst();cx=cx.coerceIn(0,cols-1);cy=cy.coerceIn(0,rows-1)}
    fun feed(bytes:ByteArray){bytes.forEach{feedByte(it.toInt() and 255)}}
    private fun feedByte(b:Int){
        if(esc){ if(!csi && b==91){csi=true;params.clear();return}; if(csi){if(b in 48..57||b==59||b==63){params.append(b.toChar());return}; handleCsi(b);esc=false;csi=false;return}; handleEsc(b.toChar());esc=false;return }
        when(b){27->esc=true;10->{newline()};13->cx=0;8->cx=(cx-1).coerceAtLeast(0);9->{cx=((cx/8)+1)*8};7->{};else->if(b>=32){put(b.toChar())}}
    }
    private fun newline(){cy++;if(cy>=rows){lines.addLast(newLine());cy=rows-1;if(lines.size>scrollback)lines.removeFirst()}}
    private fun put(ch:Char){if(cx>=cols){cx=0;newline()};lines.elementAt(lines.size-rows+cy)[cx]=Cell(ch,fg,bg,bold);cx++}
    private fun p():List<Int>{if(params.isEmpty())return emptyList();return params.toString().removePrefix("?").split(';').mapNotNull{it.toIntOrNull()}}
    private fun handleEsc(ch:Char){when(ch){'c'->reset();'7'->{}; '8'->{} }}
    private fun handleCsi(ch:Char){val a=p();val n=(a.firstOrNull()?:1).coerceAtLeast(1);when(ch){'A'->cy=(cy-n).coerceAtLeast(0);'B'->cy=(cy+n).coerceAtMost(rows-1);'C'->cx=(cx+n).coerceAtMost(cols-1);'D'->cx=(cx-n).coerceAtLeast(0);'G'->cx=((a.firstOrNull()?:1)-1).coerceIn(0,cols-1);'H','f'->{cy=((a.getOrNull(0)?:1)-1).coerceIn(0,rows-1);cx=((a.getOrNull(1)?:1)-1).coerceIn(0,cols-1)};'J'->{if((a.firstOrNull()?:0)==2)clear()};'K'->for(x in cx until cols)lines.elementAt(lines.size-rows+cy)[x]=Cell();'m'->sgr(a)} }
    private fun sgr(a:List<Int>){if(a.isEmpty()){fg=Color.rgb(230,237,243);bg=Color.TRANSPARENT;bold=false;return};a.forEach{when(it){0->{fg=Color.rgb(230,237,243);bg=Color.TRANSPARENT;bold=false};1->bold=true;22->bold=false;30->fg=Color.rgb(0,0,0);31->fg=Color.rgb(239,83,80);32->fg=Color.rgb(102,187,106);33->fg=Color.rgb(255,202,40);34->fg=Color.rgb(66,165,245);35->fg=Color.rgb(171,71,188);36->fg=Color.rgb(38,198,218);37->fg=Color.rgb(238,238,238);39->fg=Color.rgb(230,237,243);40->bg=Color.rgb(0,0,0);41->bg=Color.rgb(80,20,20);42->bg=Color.rgb(20,80,20);43->bg=Color.rgb(80,70,20);44->bg=Color.rgb(20,40,80);45->bg=Color.rgb(70,20,80);46->bg=Color.rgb(20,70,80);47->bg=Color.rgb(230,230,230);49->bg=Color.TRANSPARENT;}}
    private fun clear(){lines.clear();repeat(rows){lines.addLast(newLine())};cx=0;cy=0}
    private fun reset(){clear();fg=Color.rgb(230,237,243);bg=Color.TRANSPARENT;bold=false}
    fun snapshot():List<List<Cell>> = lines.drop(max(0,lines.size-rows)).map{it.toList()}
}
