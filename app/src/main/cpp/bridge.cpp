// SPDX-License-Identifier: MPL-2.0
#include "engine.hpp"
#include <jni.h>
#include <chrono>
#include <sstream>
using namespace mr;
namespace {
struct Session{Engine engine;Samples source;size_t index=0;double frac=0;explicit Session(Config c,std::vector<std::string> calls):engine(std::move(c),std::move(calls),uint32_t(std::chrono::steady_clock::now().time_since_epoch().count())){}}
;
Session* get(jlong h){return reinterpret_cast<Session*>(h);}
std::string str(JNIEnv* env,jstring text){if(!text)return {};const char* p=env->GetStringUTFChars(text,nullptr);if(!p)return {};std::string s(p);env->ReleaseStringUTFChars(text,p);return s;}
std::string quoted(const std::string& value){std::string s="\"";for(unsigned char c:value){if(c=='"'||c=='\\'){s+='\\';s+=char(c);}else if(c>=32)s+=char(c);}return s+'"';}
void error(JNIEnv* env,const char* msg){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),msg);}
}
extern "C" JNIEXPORT jlong JNICALL Java_org_morserunner_tablet_NativeEngine_create(JNIEnv* env,jclass,jstring call,jstring data,jintArray options){
    int size=env->GetArrayLength(options);
    if(size!=8&&size!=10){error(env,"Invalid configuration");return 0;}
    jint a[10]={};env->GetIntArrayRegion(options,0,size,a);Config c;c.call=str(env,call);c.wpm=a[0];c.activity=a[1];c.duration=a[2];c.mode=Mode(a[3]);c.pitch=a[4];c.bandwidth=a[5];c.dxWpmMin=a[8];c.dxWpmMax=a[9];
    c.qrn=(a[6]&1)!=0;c.qrm=(a[6]&2)!=0;c.qsb=(a[6]&4)!=0;c.flutter=(a[6]&8)!=0;c.lids=(a[6]&16)!=0;c.qsk=(a[6]&32)!=0;c.monitorDb=float(a[7]);
    try{return reinterpret_cast<jlong>(new Session(c,Engine::loadCalls(str(env,data))));}catch(const std::exception& e){error(env,e.what());return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_org_morserunner_tablet_NativeEngine_destroy(JNIEnv*,jclass,jlong h){delete get(h);}
extern "C" JNIEXPORT jboolean JNICALL Java_org_morserunner_tablet_NativeEngine_render(JNIEnv* env,jclass,jlong h,jshortArray output){
    auto* s=get(h);if(!s)return false;int count=env->GetArrayLength(output);std::vector<jshort> out(count);
    // 11025 Hz core remains unchanged. Only the final PCM is linearly resampled to 48000 Hz.
    for(int i=0;i<count;i++){
        while(s->source.size()<s->index+2){
            if(s->index){s->source.erase(s->source.begin(),s->source.begin()+s->index);s->index=0;}
            auto next=s->engine.audio();s->source.insert(s->source.end(),next.begin(),next.end());
        }
        float sample=s->source[s->index]+float(s->frac)*(s->source[s->index+1]-s->source[s->index]);
        out[i]=jshort(std::clamp(rnd(sample),-32768,32767));s->frac+=double(Rate)/48000;
        if(s->frac>=1){s->frac-=1;s->index++;}
    }
    env->SetShortArrayRegion(output,0,count,out.data());return s->engine.running;
}
extern "C" JNIEXPORT jboolean JNICALL Java_org_morserunner_tablet_NativeEngine_action(JNIEnv* env,jclass,jlong h,jint cmd,jstring call,jint rst,jint nr){
    auto* s=get(h);if(!s)return false;auto& e=s->engine;auto c=str(env,call);
    switch(cmd){
        case 0:return e.enter(c,rst,nr);
        case 1:return e.save(c,rst,nr);
        case 2:e.abort();break;
        case 3:e.updateCall(c);break;
        case 4:e.callSent=e.nrSent=false;e.mustAdvance=false;break;
        case 5:e.running=false;break;
        case 6:e.tune(rst,e.cfg.bandwidth,e.cfg.rit);break;
        case 7:e.tune(e.cfg.wpm,rst,e.cfg.rit);break;
        case 8:e.tune(e.cfg.wpm,e.cfg.bandwidth,rst);break;
        case 9:if(!e.callSent)e.send(Message::HisCall,c);e.send(Message::TU,c);return e.save(c,rst,nr);
        default:if(cmd>=100&&cmd<=122)e.send(Message(cmd-100),c);break;
    }return false;
}
extern "C" JNIEXPORT jstring JNICALL Java_org_morserunner_tablet_NativeEngine_snapshot(JNIEnv* env,jclass,jlong h){
    auto* s=get(h);if(!s)return env->NewStringUTF("{}");auto& e=s->engine;auto t=e.stats();std::ostringstream out;
    out<<"{\"running\":"<<(e.running?"true":"false")<<",\"seconds\":"<<e.elapsed()<<",\"pileup\":"<<e.dxCount()<<",\"serial\":"<<e.me->nr<<",\"wpm\":"<<e.cfg.wpm<<",\"bandwidth\":"<<e.cfg.bandwidth<<",\"rit\":"<<e.cfg.rit<<",\"advance\":"<<(e.advance?"true":"false")<<",\"tx\":"<<quoted(e.me->msgText)<<",\"rawPoints\":"<<t.rawPoints<<",\"rawMult\":"<<t.rawMult<<",\"rawScore\":"<<t.rawScore<<",\"points\":"<<t.points<<",\"mult\":"<<t.mult<<",\"score\":"<<t.score<<",\"rate\":"<<t.rate<<",\"log\":[";
    size_t start=e.log.size()>100?e.log.size()-100:0;
    for(size_t i=start;i<e.log.size();i++){if(i>start)out<<',';const auto& q=e.log[i];out<<"{\"time\":"<<int(q.time)<<",\"call\":"<<quoted(q.call)<<",\"rst\":"<<q.rst<<",\"nr\":"<<q.nr<<",\"sent\":"<<q.sentNr<<",\"prefix\":"<<quoted(q.prefix)<<",\"error\":"<<quoted(q.error)<<"}";}
    out<<"]}";e.advance=false;return env->NewStringUTF(out.str().c_str());
}
extern "C" JNIEXPORT jstring JNICALL Java_org_morserunner_tablet_NativeEngine_csv(JNIEnv* env,jclass,jlong h){
    auto* s=get(h);std::ostringstream out;out<<"elapsed_seconds,call,rst,received_nr,sent_nr,prefix,error\n";
    if(s)for(const auto& q:s->engine.log)out<<int(q.time)<<','<<q.call<<','<<q.rst<<','<<q.nr<<','<<q.sentNr<<','<<q.prefix<<','<<q.error<<'\n';
    return env->NewStringUTF(out.str().c_str());
}
