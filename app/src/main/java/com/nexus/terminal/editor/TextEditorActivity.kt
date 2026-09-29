package com.nexus.terminal.editor
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import java.io.File
class TextEditorActivity:Activity(){private lateinit var file:File;override fun onCreate(b:Bundle?){super.onCreate(b);file=File(intent.getStringExtra("path")?:return);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(9,12,16))};val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val title=TextView(this).apply{text=file.name;setTextColor(Color.WHITE);textSize=18f;setPadding(16,8,16,8)};bar.addView(title,LinearLayout.LayoutParams(0,56,1f));val save=Button(this).apply{text="Save";setOnClickListener{editor().text.toString().let{file.writeText(it)};Toast.makeText(this@TextEditorActivity,"Saved",Toast.LENGTH_SHORT).show()}};bar.addView(save);root.addView(bar);root.addView(editor(),LinearLayout.LayoutParams(-1,0,1f));setContentView(root)}
 private var edit:EditText?=null;private fun editor():EditText{if(edit!=null)return edit!!;edit=EditText(this).apply{setText(runCatching{file.readText()}.getOrDefault(""));setTextColor(Color.rgb(230,237,243));setBackgroundColor(Color.rgb(9,12,16));setTypeface(android.graphics.Typeface.MONOSPACE);gravity=Gravity.TOP;setPadding(16,12,16,12);setHorizontallyScrolling(true)};return edit!!}
}
