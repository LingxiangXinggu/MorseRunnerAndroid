// SPDX-License-Identifier: MPL-2.0
// Ported from BH1SCW/MorseRunner VCL/{MorseKey,MorseTbl,MovAvg,QuickAvg,VolumCtl}.pas.
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <map>
#include <random>
#include <string>
#include <vector>

namespace mr {
constexpr int Rate=11025, Block=512, Never=2147483647;
constexpr double Pi=3.14159265358979323846, TwoPi=2*Pi;
using Samples=std::vector<float>;
inline int rnd(double x) { return static_cast<int>(std::nearbyint(x)); }
inline int blocks(double seconds) { return rnd(Rate*seconds/Block); }
inline double seconds(int n) { return double(n)*Block/Rate; }
struct Random {
    std::mt19937 gen;
    explicit Random(uint32_t seed):gen(seed){}
    double unit() { return (double(gen())+0.5)/4294967296.0; }
    int integer(int n) { return int(unit()*n); }
    double normal() { return sqrt(-2*log(unit()))*cos(TwoPi*unit()); }
    double limited(double mean,double limit) { return std::clamp(mean+normal()*0.5*limit,mean-limit,mean+limit); }
    double rayleigh(double mean) { return mean*sqrt(-log(unit())-log(unit())); }
    double uShaped() { return sin(Pi*(unit()-0.5)); }
    int poisson(double mean) { double t=1,g=exp(-mean); for(int n=0;n<=30;n++){ t*=unit();if(t<=g)return n;}return 30; }
};
inline void replace(std::string& s,const std::string& from,const std::string& to,bool all=true) {
    size_t at=0;while((at=s.find(from,at))!=std::string::npos){s.replace(at,from.size(),to);at+=to.size();if(!all)break;}
}
struct Keyer {
    Samples ramp;
    std::map<char,std::string> table;
    Keyer() {
        const std::string letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/.,?=\\";
        const char* codes[]={".-","-...","-.-.","-..",".","..-.","--.","....","..",".---","-.-",".-..","--","-.","---",".--.","--.-",".-.","...","-","..-","...-",".--","-..-","-.--","--..","-----",".----","..---","...--","....-",".....","-....","--...","---..","----.","-..-.",".-.-.-","--..--","..--..","-...-","...-."};
        for(size_t i=0;i<letters.size();i++)table[letters[i]]=std::string(codes[i])+" ";
        ramp.resize(rnd(2.7*0.005*Rate));
        float sum=0;
        for(size_t i=0;i<ramp.size();i++){
            double x=double(i)/ramp.size();
            sum+=float(0.35875-0.48829*cos(TwoPi*x)+0.14128*cos(2*TwoPi*x)-0.01168*cos(3*TwoPi*x));
            ramp[i]=sum;
        }
        for(auto& x:ramp)x/=sum;
    }
    std::string encode(const std::string& text) const {
        std::string out;
        for(char c:text){if(c==' '||c=='_')out+=' ';else{auto it=table.find(c);if(it!=table.end())out+=it->second;}}
        if(!out.empty())out.back()='~';
        return out;
    }
    Samples envelope(const std::string& text,int wpm) const {
        auto code=encode(text);int units=0;
        for(char c:code)units+=(c=='.'?2:c=='-'?4:c==' '?2:c=='~'?1:0);
        int unit=rnd(1.2*Rate/std::clamp(wpm,5,120)),r=int(ramp.size());
        int len=units*unit+r;Samples out(((len+Block-1)/Block)*Block,0);int p=0;
        for(char c:code){
            if(c=='.'||c=='-'){
                for(float x:ramp)out.at(p++)=x;
                int count=(c=='.'?1:3)*unit-r;
                for(int i=0;i<count;i++)out.at(p+i)=1;
                p+=count;
                for(auto it=ramp.rbegin();it!=ramp.rend();++it)out.at(p++)=*it;
                p+=unit-r;
            }else if(c==' ')p+=2*unit-r;else if(c=='~')p+=unit-r;
        }
        return out;
    }
};
// Block implementation follows MovAvg.pas, including its periodic alternate-filter reset.
struct MovingAverage {
    int points=15;float scale=1;
    std::vector<Samples> re,im;
    void configure(int p,float gain){points=p;scale=powf(10,0.05f*gain)/powf(float(p),3);reset();}
    void reset(){re.assign(4,Samples(Block+points));im=re;}
    Samples filter(const Samples& in,std::vector<Samples>& buf){
        std::move(buf[0].begin()+Block,buf[0].end(),buf[0].begin());
        std::copy(in.begin(),in.end(),buf[0].begin()+points);
        for(int pass=1;pass<=3;pass++){
            auto& a=buf[pass-1];auto& b=buf[pass];
            std::move(b.begin()+Block,b.end(),b.begin());
            for(int i=points;i<Block+points;i++)b[i]=b[i-1]-a[i-points]+a[i];
        }
        Samples out(Block);for(int i=0;i<Block;i++)out[i]=buf[3][points+i]*scale;return out;
    }
    void apply(Samples& a,Samples& b){a=filter(a,re);b=filter(b,im);}
};
struct QuickAverage {
    int points=1,idx=0,prev=0;double scale=1;
    std::vector<std::vector<double>> re,im;
    void configure(int n){points=std::max(1,n);idx=0;prev=points-1;scale=1/pow(double(points),3);re.assign(4,std::vector<double>(points));im=re;}
    float run(float value,std::vector<std::vector<double>>& buf){
        float result=value;
        for(int p=1;p<=3;p++){value=result;result=float(buf[p][prev]-buf[p-1][idx]+value);buf[p-1][idx]=value;}
        buf[3][idx]=result;return float(result*scale);
    }
    float magnitude(float a,float b){float x=run(a,re),y=run(b,im);prev=idx;idx=(idx+1)%points;return sqrtf(x*x+y*y);}
};
struct Fading {
    QuickAverage filter;float gain=0;
    float next(Random& rng){return filter.magnitude(float(2*rng.unit()-1),float(2*rng.unit()-1))*sqrtf(3*float(filter.points));}
    void configure(double bandwidth,Random& rng){filter.configure(int(ceil(0.37*Rate/((Block/4)*bandwidth))));for(int i=0;i<=filter.points*3;i++)gain=next(rng);}
    void apply(Samples& a,Random& rng){for(int b=0;b<int(a.size())/(Block/4);b++){float dg=(next(rng)-gain)/(Block/4);for(int i=0;i<Block/4;i++){a[b*(Block/4)+i]*=gain;gain+=dg;}}}
};
struct Agc {
    static constexpr int Attack=155,Hold=155,Len=2*(Attack+Hold)+1;
    Samples data=Samples(Len),magnitude=Samples(Len),shape=Samples(Len);
    int idx=0;float beta=float(pow(10,3.8)/log(20000.0/15000));
    Agc(){for(int i=0;i<Attack;i++){shape[i]=float(log(0.5-0.5*cos((i+1)*Pi/(Attack+1))));shape[Len-1-i]=shape[i];}}
    void apply(Samples& a){
        for(auto& v:a){data[idx]=v;magnitude[idx]=logf(std::max(1e-20f,fabsf(v+1e-10f)));idx=(idx+1)%Len;
            float env=1e-10f;int di=idx;
            for(int wi=0;wi<Len;wi++){env=std::max(env,magnitude[di]+shape[wi]);di=(di+1)%Len;}
            env=expf(env);v=data[(idx+Len/2)%Len]*20000*(-expm1f(-env/beta))/env;
        }
    }
};
} // namespace mr
