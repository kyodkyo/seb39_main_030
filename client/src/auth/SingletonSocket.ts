import { io } from 'socket.io-client';

class Socket {
  private io: any;
  constructor(url) {
    this.io = io(url);
  }
}

const Singleton = {
  instance: null,

  getInstance(_param) {
    if (!this.instance) this.instance = new Socket(_param);
    return this.instance;
  },
};

const singleInstance = Singleton.getInstance('http://localhost:5002');
export const socket = singleInstance.io;
