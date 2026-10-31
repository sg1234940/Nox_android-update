package pl.iskra.avatar.test

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import org.json.JSONArray
import org.json.JSONObject
import pl.iskra.avatar.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Standalone foreground Lab. UI, model executor and existing Promyk have separate owners. */
class AvatarTestActivity : Activity(), LabVoice.Events {
 private val main=Handler(Looper.getMainLooper());private val io=Executors.newSingleThreadExecutor()
 private lateinit var avatar:AvatarSurfaceView;private lateinit var lab:AvatarLabControls
 private lateinit var root:FrameLayout;private lateinit var drawer:LinearLayout;private lateinit var shade:View
 private lateinit var input:EditText;private lateinit var action:ImageButton;private lateinit var notice:TextView
 private lateinit var bubbles:LinearLayout;private lateinit var transcript:ScrollView
 private lateinit var preferences:LabPreferences
 private var profile=LabPresentation();private var voices=listOf<LabVoice.Voice>();private var ears=listOf<String>()
 private var voice:LabVoice?=null;private var foreground=false;private var handsFree=false
 private var listening=false;private var talking=false;private var drawerOpen=false;private var textMode=false
 private var turn=0L;private var sequence=0L;private var playback:String?=null
 private var resumedTurn=false;private var pendingImport="avatar";private var permissionOwner=0L
 private var life=0L;private var initializedAppearance=false
 private var previewDialog:AlertDialog?=null
 private var backgroundVersion=0L
 private val connections by lazy {getSharedPreferences("avatar_lab_connection",MODE_PRIVATE)}
 private var speechError=false
 private val modelList by lazy {getSharedPreferences("avatar_lab_models_v1",MODE_PRIVATE)}
 private fun dp(n:Int)=(n*resources.displayMetrics.density).roundToInt()
 private fun panel(alpha:Int=190)=GradientDrawable().apply {setColor(Color.argb(alpha,15,28,43));cornerRadius=dp(18).toFloat();setStroke(dp(1),0xff35546e.toInt())}
 private fun label(text:String,size:Float=15f)=TextView(this).apply {this.text=text;textSize=size;setTextColor(0xffe4edf6.toInt());setPadding(dp(12),dp(8),dp(12),dp(8))}
 private fun button(text:String,click:()->Unit)=Button(this).apply {this.text=text;isAllCaps=false;setOnClickListener {click()}}
 private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))}
 private fun emit(c:AvatarController.Command){avatar.controller.dispatch(AvatarController.Event("avatar-lab",turn,sequence++,c))}
 private fun newTurn(){avatar.controller.beginTurn(++turn);sequence=0;playback=null}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  preferences=LabPreferences(this);profile=preferences.read();root=FrameLayout(this).apply {setBackgroundColor(Color.BLACK)}
  avatar=AvatarSurfaceView(this);avatar.controller.bindConversation("avatar-lab");avatar.controller.beginTurn(turn)
  root.addView(avatar,FrameLayout.LayoutParams(-1,-1))
  val overlay=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
  root.addView(overlay,FrameLayout.LayoutParams(-1,-1))
  val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
  header.addView(button("☰"){openDrawer()},LinearLayout.LayoutParams(dp(60),dp(52)))
  header.addView(label("Iskra Lab",23f),LinearLayout.LayoutParams(0,dp(56),1f))
  header.addView(button("⚙"){connectionSettings()},LinearLayout.LayoutParams(dp(60),dp(52)))
  overlay.addView(header);overlay.addView(View(this),LinearLayout.LayoutParams(-1,0,1f))
  notice=label("Dotknij mikrofonu, aby mówić",13f);notice.gravity=Gravity.CENTER;notice.background=panel(130);overlay.addView(notice)
  bubbles=column();transcript=ScrollView(this).apply {addView(bubbles);visibility=View.GONE}
  overlay.addView(transcript,LinearLayout.LayoutParams(-1,dp(220)))
  val composer=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=panel(145);setPadding(dp(8),dp(4),dp(4),dp(4))}
  input=EditText(this).apply {hint="Napisz do Iskry…";setHintTextColor(0xff94aec8.toInt());setTextColor(Color.WHITE);background=null;maxLines=4;minLines=1;setSingleLine(false)}
  action=ImageButton(this).apply {background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(0xff00bde9.toInt())};imageTintList=android.content.res.ColorStateList.valueOf(0xff082137.toInt())}
  composer.addView(input,LinearLayout.LayoutParams(0,-2,1f));composer.addView(action,LinearLayout.LayoutParams(dp(52),dp(52)))
  overlay.addView(composer,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(10),dp(8),dp(10),dp(8))})
  shade=View(this).apply {setBackgroundColor(0x77000000);visibility=View.GONE;setOnClickListener{closeDrawer()}};root.addView(shade,FrameLayout.LayoutParams(-1,-1))
  drawer=column().apply {setBackgroundColor(0xf5152333.toInt());visibility=View.GONE};root.addView(ScrollView(this).apply{addView(drawer);tag="drawer";visibility=View.GONE},FrameLayout.LayoutParams(dp(292),-1,Gravity.START))
  lab=AvatarLabControls(this,avatar,begin={newTurn()},send=::emit,camera={profile.angle to profile.face},setCamera={a,f->profile=profile.copy(angle=a,face=f);applyCamera();save()},refresh={})
  voice=try {Class.forName("pl.iskra.avatar.voice.PromykLabVoice").getConstructor(android.content.Context::class.java).newInstance(this) as LabVoice}catch(_:Exception){null}
  voice?.events=this;refreshVoices()
  action.setOnClickListener {if(input.text.isNotBlank())sendTyped() else if(listening&&!talking){voice?.finishListening();sayStatus("Kończę nagranie…")} else if(talking||lab.busy){interrupt();startVoice()}else startVoice()}
  input.setOnFocusChangeListener {_,focus->if(focus){interrupt();textMode=true;transcript.visibility=View.VISIBLE;refreshAction()}}
  input.addTextChangedListener(object:TextWatcher {override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshAction()};override fun afterTextChanged(s:Editable?){} })
  avatar.onStatus={message->notice.text=message;if(!initializedAppearance && message.startsWith("Model gotowy")){initializedAppearance=true;applyProfileModel();applyBackground()}}
  installGestures();applyCamera();avatar.setAppearance(profile.hologram,profile.brightness);refreshAction();buildDrawer()
  root.setOnApplyWindowInsetsListener {v,insets->v.setPadding(0,insets.systemWindowInsetTop,0,insets.systemWindowInsetBottom);insets}
  setContentView(root);root.requestApplyInsets()
 }
 private fun refreshAction(){val mode=profile.action(input.text.isNotBlank(),listening);action.setImageResource(when(mode){"SEND"->android.R.drawable.ic_menu_send;"STOP"->android.R.drawable.ic_media_pause;else->android.R.drawable.ic_btn_speak_now});action.contentDescription=when(mode){"SEND"->"Wyślij";"STOP"->"Przerwij nagrywanie";else->"Mikrofon"}}
 private fun sayStatus(text:String){notice.text=text}
 private fun save(){try{preferences.write(profile)}catch(_:Exception){sayStatus("Nie zapisano ustawień. Dotychczasowy zapis pozostaje zachowany.")}}
 private fun applyCamera(){avatar.setCamera(profile.angle,profile.face);avatar.setZoom(profile.zoom)}
 private fun interrupt(){handsFree=false;life++;voice?.stop();listening=false;talking=false;playback=null;if(::lab.isInitialized)lab.cancel();emit(AvatarController.Command.Interrupted);refreshAction()}
 private fun startVoice(){
  if(voice==null){sayStatus("Ten wariant APK nie zawiera Promyka. Potrzebna jest pełna kompilacja Labu.");return}
  if(voices.none{it.id==profile.voiceId && it.language==profile.outputLanguage} || profile.inputLanguage !in ears){sayStatus("Wybierz zainstalowane modele w Głos i język.");voiceSettings();return}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){permissionOwner=life;requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),81);return}
  input.clearFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
  handsFree=true;textMode=false;transcript.visibility=View.GONE;voice?.listen(voiceSettingsValue());refreshAction()
 }
 private fun voiceSettingsValue()=LabVoice.Settings(profile.inputLanguage,profile.outputLanguage,profile.voiceId,profile.lengthScale,profile.silenceMs)
 private fun refreshVoices(){voice?.catalogue {available,languages,error->if(isFinishing||isDestroyed)return@catalogue;voices=available;ears=languages
  if(voices.none{it.id==profile.voiceId})voices.firstOrNull{it.language==profile.outputLanguage}?.let{profile=profile.copy(voiceId=it.id);save()};error?.let(::sayStatus)}}
 private fun voiceContext():JSONObject? {
  if(!handsFree)return null
  val selected=voices.firstOrNull{it.id==profile.voiceId && it.language==profile.outputLanguage} ?: return null
  val packages=JSONArray();voices.take(32).forEach {packages.put(JSONObject().put("voice_package_id",it.id).put("language",it.language).put("length_scale_min",.8).put("length_scale_max",1.2))}
  val defaults=JSONObject();voices.forEach {if(!defaults.has(it.language))defaults.put(it.language,it.id)};defaults.put(selected.language,selected.id)
  return JSONObject().put("schema","promyk.context/v4").put("voice_identity","ISKRA").put("default_package_id",selected.id).put("packages",packages)
   .put("active_language",profile.outputLanguage).put("conversation_mode","VOICE_CONVERSATION").put("synthesis_enabled",true).put("language_defaults",defaults).put("preferred_length_scale",profile.lengthScale.toDouble())
 }
 private fun bubble(text:String,user:Boolean){val view=label(text,16f).apply{background=panel(if(user)170 else 150)};bubbles.addView(view,LinearLayout.LayoutParams(-1,-2).apply{setMargins(if(user)dp(36) else 0,dp(5),if(user)0 else dp(36),dp(5))});while(bubbles.childCount>100)bubbles.removeViewAt(0);transcript.post{transcript.fullScroll(View.FOCUS_DOWN)}}
 private fun sendTyped(){val text=input.text.toString().trim();if(text.isEmpty())return;interrupt();textMode=true;transcript.visibility=View.VISIBLE;input.setText("");submit(text)}
 private fun submit(text:String){
  if(!foreground || text.length>8000){sayStatus("Wypowiedź musi mieć najwyżej 8000 znaków.");return}
  if(lab.busy)lab.cancel()
  val base=connections.getString("url","").orEmpty();val password=GatewayPasswordStore(this).read(base)
  if(base.isBlank()){connectionSettings();return}
  bubble(text,true);sayStatus("Myślę…")
  lab.submit(base,password,text,voiceContext(),onText={},onComplete={answer,proof->
   emit(AvatarController.Command.PhaseChanged(AvatarController.Phase.IDLE));bubble(answer,false)
   if(handsFree){val plan=proof?.optJSONObject("voice_plan_candidate")
    if(plan!=null && plan.optString("schema")=="promyk.voice/v2" && plan.optString("voice_identity")=="ISKRA" && plan.optString("mode")=="SPEAK" && plan.optString("text")==answer && answer.length in 1..2000){
     val selected=voices.firstOrNull{it.id==plan.optString("voice_package_id") && it.language==plan.optString("language")}
     val scale=plan.optJSONObject("voice_motion")?.optDouble("length_scale",Double.NaN)?.toFloat() ?: Float.NaN
     if(selected!=null && scale.isFinite() && scale in .8f..1.2f)voice?.speak(answer,selected.id,scale) else {handsFree=false;sayStatus("Nieprawidłowy lub niedostępny głos. Odpowiedź pozostaje w widoku tekstowym.")}
    }else {handsFree=false;sayStatus("Iskra odpowiedziała tekstowo. Odpowiedź jest w widoku Rozmowa.")}
   }else sayStatus("Gotowe")
  },onError={handsFree=false;emit(AvatarController.Command.Interrupted);sayStatus(it)})
  emit(AvatarController.Command.PhaseChanged(AvatarController.Phase.THINKING))
 }
 override fun state(value:LabVoice.State,message:String){if(!foreground)return
  when(value){LabVoice.State.LISTENING->{listening=true;if(!talking)emit(AvatarController.Command.PhaseChanged(AvatarController.Phase.LISTENING))}
   LabVoice.State.RECOGNIZING->{listening=false;if(!talking)emit(AvatarController.Command.PhaseChanged(AvatarController.Phase.THINKING))}
   LabVoice.State.SPEAKING->{talking=true;if(handsFree&&!listening)voice?.listen(voiceSettingsValue(),true)}
   LabVoice.State.FAILED->{listening=false;if(!talking)handsFree=false}
   LabVoice.State.IDLE->{listening=false;if(!talking)emit(AvatarController.Command.PhaseChanged(AvatarController.Phase.IDLE))}
   else->Unit}
  notice.background=panel(140).apply {setStroke(dp(1),when(value){LabVoice.State.LISTENING->0xff24d2ed.toInt();LabVoice.State.SPEAKING->0xff57e2a1.toInt();LabVoice.State.RECOGNIZING,LabVoice.State.SYNTHESIZING->0xffffd468.toInt();else->0xff35546e.toInt()})}
  if(message.isNotBlank())sayStatus(message);refreshAction()
 }
 override fun transcript(text:String){if(foreground&&handsFree){listening=false;submit(text)}}
 override fun speechDetected(){if(foreground&&handsFree&&talking){lab.cancel();voice?.interruptSpeech();talking=false;playback=null;emit(AvatarController.Command.Interrupted);sayStatus("Słucham…")}}
 override fun playback(id:String,positionMs:Long,energy:Float){if(!foreground||!handsFree)return;if(playback!=id){playback=id;emit(AvatarController.Command.SpeechStarted(id))};emit(AvatarController.Command.SpeechFrame(id,positionMs,energy))}
 override fun playbackEnded(){if(!foreground)return;playback?.let{emit(AvatarController.Command.SpeechStopped(it))};playback=null;talking=false
  if(handsFree&&!listening)voice?.listen(voiceSettingsValue()) else if(!handsFree)sayStatus("Gotowe");refreshAction()}
 private fun buildDrawer(){drawer.removeAllViews();drawer.addView(label("Iskra Lab",24f));drawer.addView(label("Widok"))
  drawer.addView(button("Cała postać"){closeDrawer();profile=profile.copy(face=false);applyCamera();save()})
  drawer.addView(button("Twarz"){closeDrawer();profile=profile.copy(face=true);applyCamera();save()})
  drawer.addView(button("Przywróć kamerę"){closeDrawer();profile=profile.copy(face=false,angle=0f,zoom=1f);applyCamera();save()})
  drawer.addView(label("Panel"));drawer.addView(button("Rozmowa"){closeDrawer();textMode=true;transcript.visibility=View.VISIBLE})
  drawer.addView(button("Sterowanie"){closeDrawer();manualControls()});drawer.addView(button("Podgląd dla Iskry"){closeDrawer();preview()})
  drawer.addView(label("Wygląd"));drawer.addView(button("Awatar"){closeDrawer();chooseModel(false)});drawer.addView(button("Ubiór"){closeDrawer();chooseModel(true)})
  drawer.addView(button(if(profile.hologram)"Styl: holograficzny" else "Styl: zwykły"){closeDrawer();interrupt();profile=profile.copy(hologram=!profile.hologram);avatar.setAppearance(profile.hologram,profile.brightness);save();buildDrawer()})
  drawer.addView(button("Jasność hologramu"){closeDrawer();sliderDialog("Jasność hologramu",profile.brightness,.2f,2f){profile=profile.copy(brightness=it);avatar.setAppearance(profile.hologram,it);save()}})
  drawer.addView(button("Tło"){closeDrawer();backgroundSettings()});drawer.addView(button("Głos i język"){closeDrawer();voiceSettings()});drawer.addView(button("Połączenie"){closeDrawer();connectionSettings()})
 }
 private fun openDrawer(){interrupt();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);drawerOpen=true;shade.visibility=View.VISIBLE;val v=root.findViewWithTag<View>("drawer");v.visibility=View.VISIBLE;drawer.visibility=View.VISIBLE;v.translationX=-dp(292).toFloat();v.animate().translationX(0f).setDuration(160).start()}
 private fun closeDrawer(){drawerOpen=false;shade.visibility=View.GONE;root.findViewWithTag<View>("drawer").visibility=View.GONE}
 override fun onBackPressed(){if(drawerOpen)closeDrawer()else super.onBackPressed()}
 private fun connectionSettings(){interrupt();val c=column();val address=EditText(this).apply{hint="Adres bramki HTTPS";setSingleLine(true);setText(connections.getString("url",""))};val passwords=GatewayPasswordStore(this)
  val password=EditText(this).apply{hint="Hasło dostępu";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setText(passwords.read(address.text.toString()))}
  c.addView(address);c.addView(password);val dialog=AlertDialog.Builder(this).setTitle("Połączenie z Iskrą").setView(c).setPositiveButton("Zapisz",null).setNegativeButton("Anuluj",null).create()
  dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{try{val base=address.text.toString().trim();require(Uri.parse(base).scheme=="https");passwords.write(base,password.text.toString());connections.edit().putString("url",base).apply();dialog.dismiss()}catch(_:Exception){password.error="Sprawdź adres HTTPS i hasło (do 256 znaków)."}}};dialog.show()
 }
 private fun voiceSettings(){interrupt();val c=column();if(voice==null)c.addView(label("Ten wariant APK nie ma modułu Promyk."))
  c.addView(label("Język rozpoznawania mowy"));val inputs=(listOf(profile.inputLanguage)+ears).distinct();c.addView(spinner(inputs.map{if(it in ears)it else "$it · brak modelu"},inputs.indexOf(profile.inputLanguage)){profile=profile.copy(inputLanguage=inputs[it]);save()})
  c.addView(label("Język odpowiedzi"));val outputs=(listOf(profile.outputLanguage)+voices.map{it.language}).distinct();c.addView(spinner(outputs.map{tag->if(voices.any{it.language==tag})tag else "$tag · brak głosu"},outputs.indexOf(profile.outputLanguage)){val lang=outputs[it];val selected=voices.firstOrNull{v->v.language==lang};profile=profile.copy(outputLanguage=lang,voiceId=selected?.id.orEmpty());save()})
  c.addView(label("Głos (faktycznie zainstalowane modele)"));if(voices.isEmpty())c.addView(label("Brak modeli. Zaimportuj folder Vosk/Piper.")) else c.addView(spinner(listOf("Wybierz głos…")+voices.map{"${it.language} · ${it.name}"},voices.indexOfFirst{it.id==profile.voiceId}+1){if(it>0){val v=voices[it-1];profile=profile.copy(voiceId=v.id,outputLanguage=v.language);save()}})
  c.addView(button("Tempo mówienia"){sliderDialog("Długość głosu: mniej = szybciej",profile.lengthScale,.8f,1.2f){profile=profile.copy(lengthScale=it);save()}})
  c.addView(button("Przerwa kończąca wypowiedź"){sliderDialog("Cisza (ms)",profile.silenceMs.toFloat(),600f,3000f){profile=profile.copy(silenceMs=it.roundToInt());save()}})
  c.addView(button("Importuj folder modeli Vosk i Piper"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),84)})
  AlertDialog.Builder(this).setTitle("Głos i język · domyślnie polski").setView(ScrollView(this).apply{addView(c)}).setPositiveButton("Zamknij",null).show()
 }
 private fun spinner(values:List<String>,selected:Int,onSelect:(Int)->Unit)=Spinner(this).apply{adapter=ArrayAdapter(this@AvatarTestActivity,android.R.layout.simple_spinner_dropdown_item,values);setSelection(selected.coerceAtLeast(0));var last=selected.coerceAtLeast(0);onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){if(position!=last && position==this@apply.selectedItemPosition){last=position;onSelect(position)}};override fun onNothingSelected(p:AdapterView<*>?){} }}
 private fun sliderDialog(title:String,value:Float,min:Float,max:Float,changed:(Float)->Unit){interrupt();val bar=SeekBar(this).apply{this.max=100;progress=((value-min)/(max-min)*100).roundToInt().coerceIn(0,100)};bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(b:SeekBar?,p:Int,user:Boolean){if(user)changed(min+(max-min)*p/100f)};override fun onStartTrackingTouch(b:SeekBar?){};override fun onStopTrackingTouch(b:SeekBar?){} });AlertDialog.Builder(this).setTitle(title).setView(bar).setPositiveButton("Gotowe",null).show()}
 private fun manualControls(){interrupt();val c=column();c.addView(label("Ręczne sterowanie przejmuje awatara"));c.addView(button("Poza bazowa i reset mimiki"){emit(AvatarController.Command.Reset)})
  c.addView(spinner(listOf("Poza bazowa")+AvatarController.ANIMATIONS,(avatar.controller.snapshot().animation?.let{AvatarController.ANIMATIONS.indexOf(it)+1}?:0)){emit(AvatarController.Command.Animation(if(it==0)null else AvatarController.ANIMATIONS[it-1]))})
  c.addView(button("Zamroź / wznów ciało"){emit(AvatarController.Command.BodyPaused(!avatar.controller.snapshot().bodyPaused))})
  AvatarController.MORPHS.forEach{name->c.addView(button(name){sliderDialog(name,avatar.controller.snapshot().weights[AvatarController.MORPHS.indexOf(name)],0f,1f){emit(AvatarController.Command.Weight(name,it))}})}
  AlertDialog.Builder(this).setTitle("Sterowanie").setView(ScrollView(this).apply{addView(c)}).setPositiveButton("Zamknij",null).show()
 }
 private fun preview(){interrupt();avatar.capturePreview{image,error->if(!foreground){image?.bitmap?.recycle();return@capturePreview};if(image==null)sayStatus("Podgląd niedostępny: $error")else{val v=ImageView(this).apply{setImageBitmap(image.bitmap);adjustViewBounds=true};AlertDialog.Builder(this).setTitle("Kadr z renderera").setView(v).setPositiveButton("Zamknij",null).create().apply{setOnDismissListener{v.setImageDrawable(null);image.bitmap.recycle();previewDialog=null};previewDialog=this;show()}}}}
 private fun modelFile(id:String)=File(filesDir,"avatar_models/$id.glb")
 private fun entries():List<JSONObject> = try{val a=JSONArray(modelList.getString("entries","[]"));(0 until a.length()).map{a.getJSONObject(it)}}catch(_:Exception){emptyList()}
 private fun chooseModel(outfit:Boolean){interrupt();val list=entries().filter{if(outfit)it.optString("parent")==profile.model else it.optString("parent").isEmpty()}
  val labels=listOf(if(outfit)"Oryginalny strój modelu" else "Iskra · model w aplikacji")+list.map{it.optString("label","Model") }+listOf(if(outfit)"Dodaj wariant stroju GLB…" else "Dodaj zgodnego awatara GLB…")
  AlertDialog.Builder(this).setTitle(if(outfit)"Ubiór · warianty modelu" else "Awatar").setItems(labels.toTypedArray()){_,i->if(i==labels.lastIndex){pendingImport=if(outfit)"outfit" else "avatar";startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),82)}else{
   val id=if(i==0)"" else list[i-1].getString("id");val next=if(outfit)profile.copy(outfit=id)else profile.copy(model=if(id.isEmpty())"builtin" else id,outfit="")
   selectProfile(next)}}.setNegativeButton("Anuluj",null).show()
 }
 private fun selectProfile(next:LabPresentation){try{next.checked()}catch(_:Exception){sayStatus("Nieprawidłowy zapis wyglądu.");return};interrupt();val id=next.outfit.ifEmpty{next.model};val file=if(id=="builtin")null else modelFile(id)
  if(file!=null&&!file.isFile){sayStatus("Plik modelu nie jest już dostępny. Zachowano poprzedni.");return}
  avatar.loadModel(file){ok->if(ok){profile=next;emit(AvatarController.Command.Reset);applyCamera();avatar.setAppearance(profile.hologram,profile.brightness);save();buildDrawer()}}
 }
 private fun applyProfileModel(){val id=profile.outfit.ifEmpty{profile.model};if(id!="builtin")selectProfile(profile)}
 private fun backgroundSettings(){interrupt();AlertDialog.Builder(this).setTitle("Tło").setItems(arrayOf("Czarne","Spokojna scena","Własne zdjęcie…")){_,i->when(i){0->{profile=profile.copy(background="black");applyBackground();save()};1->{profile=profile.copy(background="scene");applyBackground();save()};else->{sayStatus("Wybrane zdjęcie będzie widoczne w podglądach przekazywanych Iskrze.");startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),83)}}}.show()}
 private fun applyBackground(){val revision=++backgroundVersion;val kind=profile.background;if(kind=="black"){avatar.setSceneBackground(null);return};val owner=life
  io.execute{val bitmap=try{if(kind=="scene")Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).also{val canvas=Canvas(it);val paint=Paint().apply{shader=LinearGradient(0f,0f,0f,512f,0xff08111b.toInt(),0xff17435a.toInt(),Shader.TileMode.CLAMP)};canvas.drawRect(0f,0f,512f,512f,paint)}else BitmapFactory.decodeFile(File(filesDir,"avatar_background.jpg").absolutePath)}catch(_:Exception){null}
   main.post{if(isDestroyed||revision!=backgroundVersion){bitmap?.recycle();return@post};if(bitmap!=null)avatar.setSceneBackground(bitmap)else sayStatus("Tło niedostępne. Zachowano poprzedni widok.")}}
 }
 private fun installGestures(){var x=0f;var down=0f;var moved=false
  val scale=ScaleGestureDetector(this,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{profile=profile.copy(zoom=(profile.zoom*d.scaleFactor).coerceIn(.65f,1.8f));applyCamera();return true};override fun onScaleBegin(d:ScaleGestureDetector):Boolean{interrupt();return true};override fun onScaleEnd(d:ScaleGestureDetector){save()}})
  avatar.setOnTouchListener{_,e->scale.onTouchEvent(e);when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;down=e.x;moved=false;true};MotionEvent.ACTION_MOVE->{if(!scale.isInProgress){val dx=e.x-x;if(kotlin.math.abs(e.x-down)>dp(8)){if(!moved)interrupt();moved=true;profile=profile.copy(angle=(profile.angle+dx/500f).coerceIn(-1.2f,1.2f));applyCamera()};x=e.x};true};MotionEvent.ACTION_UP->{if(down<dp(24)&&e.x-down>dp(45))openDrawer()else if(moved)save();true};else->true}}
 }
 override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,permissions,results);if(code==81&&foreground&&permissionOwner==life){if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)startVoice()else sayStatus("Bez zgody na mikrofon można nadal pisać.")}}
 override fun onActivityResult(code:Int,result:Int,data:Intent?){super.onActivityResult(code,result,data);val uri=data?.data ?: return;if(result!=RESULT_OK)return
  if(code==84){voice?.importModels(uri){error->if(error!=null)sayStatus(error)else{sayStatus("Modele zaimportowane");refreshVoices()}};return}
  val owner=life;val kind=pendingImport;val baseModel=profile.model
  io.execute {try {if(code==82){val bytes=contentResolver.openInputStream(uri)!!.use{it.readBytesBounded(157286400)};GlbFaceContractReader.read(bytes);val id=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    val file=modelFile(id);file.parentFile!!.mkdirs();val temp=File(file.parentFile,"$id.part");temp.writeBytes(bytes);check(temp.renameTo(file))
    main.post{if(isDestroyed||life!=owner)return@post;val list=entries().filterNot{it.optString("id")==id};val entry=JSONObject().put("id",id).put("label",if(kind=="outfit")"Strój ${list.size+1}" else "Awatar ${list.size+1}").put("parent",if(kind=="outfit")baseModel else "");modelList.edit().putString("entries",JSONArray(list+entry).toString()).apply();selectProfile(if(kind=="outfit")profile.copy(outfit=id)else profile.copy(model=id,outfit=""))}
   }else if(code==83){val bytes=contentResolver.openInputStream(uri)!!.use{it.readBytesBounded(20971520)};val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,options);require(options.outWidth>0&&options.outHeight>0);var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>2048)sample*=2
    val bitmap=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample}));try{java.io.FileOutputStream(File(filesDir,"avatar_background.part")).use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it));it.fd.sync()};check(File(filesDir,"avatar_background.part").renameTo(File(filesDir,"avatar_background.jpg")))}finally{bitmap.recycle()}
    main.post{if(!isDestroyed&&life==owner){profile=profile.copy(background="photo");applyBackground();save()}}
   }}catch(_:Exception){main.post{if(!isDestroyed)sayStatus("Nie udało się wczytać pliku. Sprawdź format i rozmiar.")}}catch(_:OutOfMemoryError){main.post{if(!isDestroyed)sayStatus("Za mało pamięci na ten plik.")}}}
 }
 private fun java.io.InputStream.readBytesBounded(limit:Int):ByteArray{val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536);var total=0;while(true){val n=read(buffer);if(n<0)break;total+=n;require(total<=limit);out.write(buffer,0,n)};return out.toByteArray()}
 override fun onResume(){super.onResume();foreground=true;if(resumedTurn)newTurn();resumedTurn=true;avatar.setResumed(true);lab.resumed(true)}
 override fun onPause(){foreground=false;previewDialog?.dismiss();previewDialog=null;interrupt();lab.resumed(false);avatar.setResumed(false);super.onPause()}
 override fun onDestroy(){voice?.close();lab.destroy();io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy()}
}
