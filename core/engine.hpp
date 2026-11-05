// SPDX-License-Identifier: MPL-2.0
// Core port of BH1SCW/MorseRunner 1.71, commit f5e96d3a08b1d60890975b345acdcf32aac37883.
#pragma once
#include "dsp.hpp"
#include <memory>
#include <set>

namespace mr {
enum class Mode{Pileup=1,Single=2,Wpx=3,Hst=4};
enum class Message{None,CQ,NR,TU,MyCall,HisCall,B4,Qm,Nil,Garbage,R_NR,R_NR2,DeMyCall1,DeMyCall2,DeMyCallNr1,DeMyCallNr2,NrQm,LongCQ,MyCallNr2,Qrl,Qrl2,Qsy,Agn};
using Messages=uint32_t;
inline Messages bit(Message m){return 1u<<static_cast<unsigned>(m);}
inline bool has(Messages m,Message v){return (m&bit(v))!=0;}
enum class State{Listening,Copying,Preparing,Sending};
enum class Event{Timeout,Sent,MeStarted,MeFinished};
enum class OperatorState{NeedPrevEnd,NeedQso,NeedNr,NeedCall,NeedCallNr,NeedEnd,Done,Failed};
enum class Match{No,Yes,Almost};
struct Config {
    std::string call="N0CALL";int wpm=30,activity=3,duration=10,pitch=600,bandwidth=500,rit=0;
    // Both zero preserve the original 1.71 speed rules for older callers.
    int dxWpmMin=0,dxWpmMax=0;
    bool qrn=false,qrm=false,qsb=false,flutter=false,lids=false,qsk=false;
    float monitorDb=-20;Mode mode=Mode::Pileup;
};
struct Qso{double time=0;std::string call,prefix,error="NIL",trueCall;int rst=599,nr=0,sentNr=0,trueRst=0,trueNr=0;bool dupe=false;};
struct Stats{int rawPoints=0,rawMult=0,rawScore=0,points=0,mult=0,score=0,rate=0;};
class Engine;
class Station{
public:
    Engine& e;State state=State::Listening;int timeout=Never,wpm=30,nr=1,rst=599,sendPos=0,pitch=0;
    float amplitude=0,phase=0;bool nrError=false,dead=false;
    std::string myCall,hisCall,msgText;Messages messages=0;Samples envelope;
    explicit Station(Engine& engine):e(engine){}virtual ~Station()=default;
    virtual void event(Event)=0;virtual void text(std::string);
    virtual Samples block();void send(Message);void morse(const std::string&);void tick();std::string numberText();float bfo();
};
class Operator{
public:
    Engine& e;std::string call;int skills=1,patience=5,repeat=1,wpm=30;OperatorState state=OperatorState::NeedPrevEnd;
    explicit Operator(Engine& engine):e(engine){}
    void set(OperatorState);void received(Messages);Message reply();void dec();
    int delay();int replyTimeout();Match isMyCall();
};
class Dx:public Station{
public:Operator oper;Fading fading;explicit Dx(Engine&);void event(Event)override;Samples block()override;
};
class Me:public Station{
public:std::vector<std::string> pieces;explicit Me(Engine&);void event(Event)override;void text(std::string)override;Samples block()override;void next();void abort();bool updateCall(const std::string&);
};
class Qrn:public Station{public:explicit Qrn(Engine&);void event(Event)override;};
class Qrm:public Station{int patience;public:explicit Qrm(Engine&);void event(Event)override;};
class Engine{
public:
    Config cfg;Random rng;Keyer keyer;std::unique_ptr<Me> me;
    std::vector<std::unique_ptr<Station>> stations;std::vector<std::string> calls;std::vector<Qso> log;
    MovingAverage filter,alternate;Agc agc;int blockNumber=0,carrierSample=0;float ritPhase=0;
    bool running=true,callSent=false,nrSent=false,advance=false,mustAdvance=false;
    Engine(Config,std::vector<std::string>,uint32_t seed);
    Samples audio();void finished();void started();Dx& addCaller();std::string pickCall();int dxCount()const;
    void send(Message,const std::string& call);void abort();bool enter(const std::string&,int rst,int nr,bool saveOnly=false);
    bool save(const std::string&,int rst,int nr);void collectTruth();void updateCall(const std::string&);Stats stats()const;
    void tune(int wpm,int bandwidth,int rit);void cleanup();double elapsed()const{return seconds(blockNumber);}
    static std::vector<std::string> loadCalls(const std::string&);
    static Match matchCall(const std::string& entered,const std::string& actual,bool lids=false);
    static std::string prefix(const std::string&);static void check(Qso&);static int callScore(const std::string&);
};
} // namespace mr
