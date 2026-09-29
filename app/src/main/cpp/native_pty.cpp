#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <thread>
#include <mutex>
#include <atomic>
struct Session { int fd=-1; pid_t pid=-1; JavaVM* vm=nullptr; jobject cb=nullptr; jmethodID method=nullptr; std::atomic<bool> running{false}; std::mutex mu; std::thread th; };
static JavaVM* g_vm=nullptr;
static void reader(Session* s){ JNIEnv* e=nullptr; bool a=false; if(s->vm->GetEnv((void**)&e,JNI_VERSION_1_6)!=JNI_OK){s->vm->AttachCurrentThread((void**)&e,nullptr);a=true;} char b[8192]; while(s->running){ ssize_t n=read(s->fd,b,sizeof(b)); if(n>0){ jbyteArray x=e->NewByteArray((jsize)n); e->SetByteArrayRegion(x,0,(jsize)n,(jbyte*)b); e->CallVoidMethod(s->cb,s->method,x); e->DeleteLocalRef(x); if(e->ExceptionCheck())e->ExceptionClear(); } else if(n<0&&errno==EINTR) continue; else break;} if(s->cb){const char* msg="\r\n[process exited]\r\n";jbyteArray x=e->NewByteArray((jsize)strlen(msg));e->SetByteArrayRegion(x,0,(jsize)strlen(msg),(jbyte*)msg);e->CallVoidMethod(s->cb,s->method,x);e->DeleteLocalRef(x);} s->running=false; if(s->fd>=0){close(s->fd);s->fd=-1;} if(a)s->vm->DetachCurrentThread(); }
extern "C" jint JNI_OnLoad(JavaVM* vm,void*){g_vm=vm;return JNI_VERSION_1_6;}
extern "C" JNIEXPORT jlong JNICALL Java_com_nexus_terminal_engine_NativePty_start(JNIEnv* e,jobject thiz,jstring shell,jstring home,jstring cwd){ const char* sh=e->GetStringUTFChars(shell,nullptr),*hm=e->GetStringUTFChars(home,nullptr),*wd=e->GetStringUTFChars(cwd,nullptr); Session* s=new Session(); s->vm=g_vm; int m=-1; pid_t p=forkpty(&m,nullptr,nullptr,nullptr); if(p==0){setenv("HOME",hm,1);setenv("NEXUS_HOME",hm,1);setenv("PATH","/data/data/com.nexus.terminal/files/bin:/system/bin:/system/xbin:/vendor/bin",1);setenv("TERM","xterm-256color",1);setenv("COLORTERM","truecolor",1);setenv("SHELL",sh,1);setenv("LANG","C.UTF-8",1);chdir(wd);execl(sh,sh,"-i",(char*)nullptr);_exit(127);} e->ReleaseStringUTFChars(shell,sh);e->ReleaseStringUTFChars(home,hm);e->ReleaseStringUTFChars(cwd,wd); if(p<0){delete s;return 0;} s->fd=m;s->pid=p;s->running=true; jclass c=e->GetObjectClass(thiz);s->method=e->GetMethodID(c,"onNativeOutput","([B)V");s->cb=e->NewGlobalRef(thiz); s->th=std::thread(reader,s); return (jlong)s; }
static Session* S(jlong h){return reinterpret_cast<Session*>(h);}
extern "C" JNIEXPORT jint JNICALL Java_com_nexus_terminal_engine_NativePty_write(JNIEnv* e,jobject,jlong h,jbyteArray a){Session*s=S(h);if(!s||!s->running)return -1;jsize n=e->GetArrayLength(a);jbyte*p=e->GetByteArrayElements(a,nullptr);ssize_t r=write(s->fd,p,n);e->ReleaseByteArrayElements(a,p,JNI_ABORT);return(jint)r;}
extern "C" JNIEXPORT jint JNICALL Java_com_nexus_terminal_engine_NativePty_resize(JNIEnv*,jobject,jlong h,jint rows,jint cols,jint px,jint py){Session*s=S(h);if(!s)return-1;struct winsize w{};w.ws_row=rows;w.ws_col=cols;w.ws_xpixel=px;w.ws_ypixel=py;return ioctl(s->fd,TIOCSWINSZ,&w);}
extern "C" JNIEXPORT void JNICALL Java_com_nexus_terminal_engine_NativePty_stop(JNIEnv*,jobject,jlong h){Session*s=S(h);if(!s)return;s->running=false;if(s->pid>0)kill(s->pid,SIGHUP);if(s->fd>=0){close(s->fd);s->fd=-1;}if(s->th.joinable())s->th.join();if(s->cb){JNIEnv*e=nullptr;bool a=false;if(s->vm->GetEnv((void**)&e,JNI_VERSION_1_6)!=JNI_OK){s->vm->AttachCurrentThread((void**)&e,nullptr);a=true;}e->DeleteGlobalRef(s->cb);s->cb=nullptr;if(a)s->vm->DetachCurrentThread();}if(s->pid>0)waitpid(s->pid,nullptr,0);delete s;}
