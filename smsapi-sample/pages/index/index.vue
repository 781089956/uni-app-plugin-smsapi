<template>
	<view class="content">
		来源：<input v-model="source" type="text" />
		号码：<input v-model="number" type="text" />
		内容：<input v-model="content" type="text" />
		时间戳：<input v-model="time" type="text" />
		会话ID：<input v-model="conversationID" type="text" />
		SIM_ID：<input v-model="SIMCardID" type="text" />
		id(删除)：<input v-model="id" type="number" />


		<button class="btn" @click='add()'>添加短信测试</button>
		<button class="btn" @click='isDefaultApp()'>检测是否默认App</button>
		<button class="btn" @click='setDefaultApp()'>设置当前应用为默认短信</button>
		<button class="btn" @click='restoreDefaultApp()'>恢复默认短信应用</button>
		<button class="btn" @click='checkPermission()'>检查短信权限</button>
		<button class="btn" @click='requestPermission()'>获取短信权限</button>
		<button class="btn" @click='readSmsMsg()'>读取短信（测试用）</button>
		<button class="btn" @click='getAllMsg()'>获取所有短信</button>
		<button class="btn" @click='register()'>注册短信接收</button>
		
		<div class="pstate" :style="{backgroundColor: hasPermission ? 'green': 'red'}">权限状态</div>
		<button class="btn" @click='del()'>删除测试</button>

	</view>
</template>

<script>
	const smsapi = uni.requireNativePlugin('SMS-API');
	const modal = uni.requireNativePlugin('modal');
	export default {
		data() {
			return {
				source: '1',
				number: '6505551212',
				content: '@@@@',
				time: '1614182434065',
				conversationID: '1',
				SIMCardID: '0',
				id: '1',
				hasPermission: false,
			}
		},
		onLoad() {

		},
		methods: {
			register() {
				smsapi.registerOnReceiveCallback(data => {
					this.log(data);
				
					modal.toast({
						message: smsapi.addSmsMsg(data),
						duration: 1.5
					});
				})
			},
			
			setDefaultApp() {
				smsapi.setDefaultApp();
				
			},
			restoreDefaultApp() {
				smsapi.restoreDefaultApp();
			},
			getAllMsg() {
				let ret = smsapi.getAllMsg();
				modal.toast({
					message: ret,
					duration: 1.5
				});
				this.log(JSON.stringify(ret));
			},

			checkPermission() {
				this.log("开始检查权限");
				let res = smsapi.checkPermission();
				if (!res) {
					this.log(' 权限未获取');
					this.hasPermission = false;
				} else {
					this.log('权限已获取');
					this.hasPermission = true;
				}
				modal.toast({
					message: res,
					duration: 1.5
				})
			},
			requestPermission() {
				this.log("开始请求权限");
				smsapi.requestPermission(data => {
					if (data.code === 'success') {
						this.log('权限请求成功');
						this.hasPermission = true;
					} else {
						this.log('权限请求失败');
						this.hasPermission = false;
					}
					modal.toast({
						message: data,
						duration: 1.5
					})
				});
			},
			test() {
				smsapi.test();
			},
			add() {

				let obj = {
					source: this.source,
					number: this.number,
					content: this.content,
					time: this.time,
					conversationID: this.conversationID,
					SIMCardID: this.SIMCardID
				}
				let ret = smsapi.addSmsMsg(obj);
				
				modal.toast({
					message: ret,
					duration: 1.5
				})
			},
			del() {
				let ret = smsapi.delSmsMsg(this.id);
				modal.toast({
					message: '删除目标ID:' + ret,
					duration: 1.5
				})
			},
			readSmsMsg() {
				smsapi.readSmsMsg();
			},
			isDefaultApp() {
				modal.toast({
					message: smsapi.isDefaultApp(),
					duration: 1.5
				})
			},
			
			log(msg) {
				console.log(msg);
				smsapi.log(msg);
			}
			

		}
	}
</script>

<style>
	.pstate {
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		color: #555555;
		width: 100%;
	}
</style>
