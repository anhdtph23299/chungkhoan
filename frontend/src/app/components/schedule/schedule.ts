import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScheduleService, ScheduleEvent } from '../../services/schedule';

export interface RoutineSlot {
  time: string;
  phase: string;
  badge: string;
  badgeClass: string;
  action: string;
  rule: string;
  isCurrent?: boolean;
}

@Component({
  selector: 'app-schedule',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './schedule.html',
  styleUrl: './schedule.scss'
})
export class ScheduleComponent implements OnInit {
  dailyRoutines: RoutineSlot[] = [
    {
      time: '08:00 - 08:45',
      phase: 'Chuẩn Bị Trước Giờ GD',
      badge: 'Chuẩn bị',
      badgeClass: 'pill-purple',
      action: 'Đọc vĩ mô quốc tế (Dow Jones, S&P 500, DXY, Giá Dầu, Tỷ giá USD/VND). Lọc danh sách Watchlist chuẩn bị sẵn điểm Pivot.',
      rule: 'Không bao giờ bước vào phiên giao dịch mà không có danh sách chuẩn bị từ trước!'
    },
    {
      time: '09:00 - 09:15',
      phase: 'Phiên ATO (Mở Cửa)',
      badge: 'ATO',
      badgeClass: 'pill-ref',
      action: 'Quan sát khoảng trống giá (GAP mở cửa) và tâm lý đám đông. Tuyệt đối không giao dịch nếu không có kế hoạch cụ thể.',
      rule: 'CẤM FOMO: Tuyệt đối không mua đuổi giá xanh/tím trong phiên ATO vì 70% là bẫy giá giả tạo.'
    },
    {
      time: '09:15 - 11:30',
      phase: 'Phiên Sáng Khớp Lệnh Liên Tục',
      badge: 'Phiên Sáng',
      badgeClass: 'pill-primary',
      action: 'Nhận diện dòng tiền dẫn dắt (Leader ngành: Bank, Chứng, Thép, BĐS, Công nghệ). Canh mua thăm dò 20-30% khi cổ phiếu test hỗ trợ tốt.',
      rule: 'Chỉ giải ngân khi khối lượng (Volume) khớp lớn gấp 1.5 lần so với trung bình các phiên trước.'
    },
    {
      time: '11:30 - 13:00',
      phase: 'Nghỉ Trưa & Thống Kê',
      badge: 'Nghỉ trưa',
      badgeClass: 'pill-purple',
      action: 'Đánh giá thanh khoản phiên sáng (đạt bao nhiêu % so với TB 20 phiên). Xem khối ngoại và tự doanh mua/bán ròng.',
      rule: 'Tâm lý thư giãn, không nhìn bảng điện vô ích lúc thị trường đóng cửa.'
    },
    {
      time: '13:00 - 14:15',
      phase: 'Phiên Chiều - Hàng T+2.5 Về',
      badge: 'Áp lực T+2.5',
      badgeClass: 'pill-down',
      action: 'Thời điểm hàng về tài khoản của 2 phiên trước. Quan sát áp lực chốt lời hoặc cắt lỗ của nhà đầu tư nhỏ lẻ.',
      rule: 'Nếu thị trường bị bán tháo thanh khoản lớn, kiên quyết giữ tiền mặt, không vội bắt đáy sớm.'
    },
    {
      time: '14:15 - 14:30',
      phase: '15 Phút Vàng Quyết Định Xu Hướng',
      badge: 'Thời Khắc Quyết Định',
      badgeClass: 'pill-up',
      action: 'Dòng tiền tạo lập (Big Boys) xuất hiện rõ nét nhất. Thời điểm chuẩn nhất để xác nhận Breakout hợp lệ hay Bull Trap.',
      rule: 'Thời điểm tốt nhất trong ngày để đặt lệnh mua gia tăng hoặc quyết định dứt khoát cắt lỗ!'
    },
    {
      time: '14:30 - 14:45',
      phase: 'Phiên ATC (Đóng Cửa)',
      badge: 'ATC',
      badgeClass: 'pill-ref',
      action: 'Chốt giá đóng cửa ngày. Đặt lệnh khớp ATC nếu cần thiết lập vị thế cho phiên bùng nổ theo đà.',
      rule: 'Kiểm tra giá đóng cửa so với ngưỡng Stop Loss 7% để chuẩn bị kịch bản cho phiên hôm sau.'
    },
    {
      time: '15:00 - 17:00',
      phase: 'Ghi Nhật Ký & Đánh Giá Kỷ Luật',
      badge: 'Review Ngày',
      badgeClass: 'pill-purple',
      action: 'Ghi chép 100% các lệnh vào Nhật Ký Giao Dịch. Ghi lại sai sót tâm lý: Có bị nôn nóng không? Có tuân thủ SL không?',
      rule: 'Kỷ luật tự giác ghi chép chính là chìa khóa tách biệt trader có lãi với 95% đám đông thua lỗ.'
    }
  ];

  events: ScheduleEvent[] = [];
  showAddModal = false;
  newEvent: Partial<ScheduleEvent> = {
    title: '',
    eventDate: new Date().toISOString().split('T')[0],
    eventType: 'Đáo hạn phái sinh',
    notes: ''
  };

  showPromptBox = false;

  constructor(private scheduleService: ScheduleService) {}

  ngOnInit(): void {
    this.highlightCurrentPhase();
    this.loadEvents();
  }

  highlightCurrentPhase(): void {
    const now = new Date();
    const h = now.getHours();
    const m = now.getMinutes();
    const curMin = h * 60 + m;

    this.dailyRoutines.forEach(r => {
      const parts = r.time.split(' - ');
      const [sh, sm] = parts[0].split(':').map(Number);
      const [eh, em] = parts[1].split(':').map(Number);
      const startMin = sh * 60 + sm;
      const endMin = eh * 60 + em;
      r.isCurrent = (curMin >= startMin && curMin <= endMin);
    });
  }

  loadEvents(): void {
    this.scheduleService.getAll().subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.events = data;
        } else {
          this.initMockEvents();
        }
      },
      error: () => this.initMockEvents()
    });
  }

  initMockEvents(): void {
    this.events = [
      {
        id: 1,
        title: 'Đáo Hạn Hợp Đồng Phái Sinh VN30F (Tháng 10)',
        eventDate: '2026-10-15',
        eventType: 'Đáo Hạn Phái Sinh',
        notes: 'Biến động mạnh phiên ATC, chỉ số trụ bị giằng co mạnh. Hạn chế mở mới vị thế.'
      },
      {
        id: 2,
        title: 'Kỳ Cơ Cấu Danh Mục Các Quỹ ETF Ngoại',
        eventDate: '2026-10-23',
        eventType: 'Cơ Cấu Quỹ ETF',
        notes: 'Khối lượng đột biến các mã Bluechip trong rổ VN30 và Diamond ETF.'
      },
      {
        id: 3,
        title: 'Mùa Công Bố Báo Cáo Tài Chính & KQKD Quý 3/2026',
        eventDate: '2026-10-28',
        eventType: 'Báo Cáo Tài Chính',
        notes: 'Tập trung các doanh nghiệp có tăng trưởng lợi nhuận đột biến > 25% (Công nghệ, Thép, Bán lẻ).'
      },
      {
        id: 4,
        title: 'Kỳ Họp Lãi Suất FOMC - Cục Dự Trữ Liên Bang Mỹ FED',
        eventDate: '2026-11-05',
        eventType: 'Vĩ Mô Quốc Tế',
        notes: 'Dự báo lộ trình hạ lãi suất của FED, tác động trực tiếp tới dòng vốn ngoại và tỷ giá USD/VND.'
      }
    ];
  }

  saveEvent(): void {
    if (!this.newEvent.title || !this.newEvent.eventDate) {
      alert('Vui lòng nhập tên sự kiện và ngày!');
      return;
    }
    this.scheduleService.create(this.newEvent as ScheduleEvent).subscribe({
      next: (res) => {
        this.events.push(res);
        this.showAddModal = false;
      },
      error: () => {
        const ev: ScheduleEvent = {
          id: Date.now(),
          title: this.newEvent.title!,
          eventDate: this.newEvent.eventDate!,
          eventType: this.newEvent.eventType || 'Sự kiện thị trường',
          notes: this.newEvent.notes || ''
        };
        this.events.push(ev);
        this.showAddModal = false;
      }
    });
  }

  removeEvent(id?: number): void {
    if (!id) return;
    if (confirm('Xóa sự kiện này khỏi lịch?')) {
      this.scheduleService.delete(id).subscribe();
      this.events = this.events.filter(e => e.id !== id);
    }
  }

  togglePromptBox(): void {
    this.showPromptBox = !this.showPromptBox;
  }
}
