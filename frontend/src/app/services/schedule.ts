import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ScheduleEvent {
  id?: number;
  title: string;
  eventDate: string;
  eventType: string;
  notes?: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class ScheduleService {
  private baseUrl = 'http://localhost:8085/api/schedule';

  constructor(private http: HttpClient) {}

  getAll(): Observable<ScheduleEvent[]> {
    return this.http.get<ScheduleEvent[]>(this.baseUrl);
  }

  getByDate(date: string): Observable<ScheduleEvent[]> {
    return this.http.get<ScheduleEvent[]>(`${this.baseUrl}/date/${date}`);
  }

  getByMonth(year: number, month: number): Observable<ScheduleEvent[]> {
    return this.http.get<ScheduleEvent[]>(`${this.baseUrl}/month?year=${year}&month=${month}`);
  }

  create(event: ScheduleEvent): Observable<ScheduleEvent> {
    return this.http.post<ScheduleEvent>(this.baseUrl, event);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
